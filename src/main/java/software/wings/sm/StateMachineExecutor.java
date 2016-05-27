package software.wings.sm;

import com.google.inject.Injector;
import com.google.inject.Singleton;

import org.mongodb.morphia.query.UpdateOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.ErrorConstants;
import software.wings.dl.WingsDeque;
import software.wings.dl.WingsPersistence;
import software.wings.exception.WingsException;
import software.wings.utils.JsonUtils;
import software.wings.waitnotify.NotifyCallback;
import software.wings.waitnotify.WaitNotifyEngine;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * Class responsible for executing state machine.
 *
 * @author Rishi
 */
@Singleton
public class StateMachineExecutor {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  @Inject private ExecutorService executorService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private ExecutionContextFactory executionContextFactory;
  @Inject private Injector injector;

  public StateExecutionInstance execute(String appId, String smId, String executionUuid) {
    return execute(appId, smId, executionUuid, null);
  }

  public StateExecutionInstance execute(
      String appId, String smId, String executionUuid, List<ContextElement> contextParams) {
    return execute(wingsPersistence.get(StateMachine.class, appId, smId), executionUuid, contextParams, null);
  }

  public StateExecutionInstance execute(String appId, String smId, String executionUuid,
      List<ContextElement> contextParams, StateMachineExecutionCallback callback) {
    return execute(wingsPersistence.get(StateMachine.class, appId, smId), executionUuid, contextParams, callback);
  }

  public StateExecutionInstance execute(StateMachine sm, String executionUuid, List<ContextElement> contextParams,
      StateMachineExecutionCallback callback) {
    if (sm == null) {
      logger.error("StateMachine passed for execution is null");
      throw new WingsException(ErrorConstants.INVALID_ARGUMENT);
    }

    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(sm.getAppId());
    stateExecutionInstance.setStateMachineId(sm.getUuid());
    stateExecutionInstance.setExecutionUuid(executionUuid);

    WingsDeque<ContextElement> contextElements = new WingsDeque<>();
    if (contextParams != null) {
      contextElements.addAll(contextParams);
    }
    stateExecutionInstance.setContextElements(contextElements);

    stateExecutionInstance.setCallback(callback);

    return execute(sm, stateExecutionInstance);
  }

  public StateExecutionInstance execute(StateMachine stateMachine, StateExecutionInstance stateExecutionInstance) {
    if (stateExecutionInstance == null) {
      throw new WingsException(ErrorConstants.INVALID_ARGUMENT, ErrorConstants.ARGS_NAME, "stateExecutionInstance");
    }
    if (stateMachine == null) {
      throw new WingsException(ErrorConstants.INVALID_ARGUMENT, ErrorConstants.ARGS_NAME, "stateMachine");
    }

    if (stateExecutionInstance.getStateName() == null) {
      stateExecutionInstance.setStateName(stateMachine.getInitialStateName());
    }

    if (stateExecutionInstance.getUuid() == null) {
      stateExecutionInstance = wingsPersistence.saveAndGet(StateExecutionInstance.class, stateExecutionInstance);
    }

    ExecutionContextImpl context = executionContextFactory.create(stateExecutionInstance, stateMachine);
    executorService.execute(new SmExecutionDispatcher(context, this));
    return stateExecutionInstance;
  }

  void startExecution(ExecutionContextImpl context) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    StateMachine stateMachine = context.getStateMachine();
    updateStatus(stateExecutionInstance, ExecutionStatus.RUNNING, "startTs");

    State currentState = null;
    try {
      currentState = stateMachine.getState(stateExecutionInstance.getStateName());
      injector.injectMembers(currentState);
      ExecutionResponse executionResponse = currentState.execute(context);
      handleExecuteResponse(context, executionResponse);
    } catch (Exception exeception) {
      handleExecuteResponseException(context, exeception);
    }
  }

  /**
   * Resumes execution of a StateMachineInstance.
   *
   * @param stateExecutionInstanceId stateMachineInstance to resume.
   * @param response                 map of responses from state machine instances this state was waiting on.
   */
  public void resume(String appId, String stateExecutionInstanceId, Map<String, ? extends Serializable> response) {
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.get(StateExecutionInstance.class, appId, stateExecutionInstanceId);
    StateMachine sm = wingsPersistence.get(StateMachine.class, appId, stateExecutionInstance.getStateMachineId());
    State currentState = sm.getState(stateExecutionInstance.getStateName());
    ExecutionContextImpl context = executionContextFactory.create(stateExecutionInstance, sm);
    try {
      ExecutionResponse executionResponse = currentState.handleAsynchResponse(context, response);
      handleExecuteResponse(context, executionResponse);
    } catch (Exception ex) {
      handleExecuteResponseException(context, ex);
    }
  }

  private StateExecutionInstance handleExecuteResponse(
      ExecutionContextImpl context, ExecutionResponse executionResponse) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    StateMachine sm = context.getStateMachine();
    State currentState = sm.getState(stateExecutionInstance.getStateName());

    updateStateExecutionData(stateExecutionInstance, executionResponse.getStateExecutionData());
    if (executionResponse.isAsynch()) {
      if (executionResponse.getCorrelationIds() == null || executionResponse.getCorrelationIds().size() == 0) {
        logger.error("executionResponse is null, but no correlationId - currentState : " + currentState.getName()
            + ", stateExecutionInstanceId: " + stateExecutionInstance.getUuid());
        updateStatus(stateExecutionInstance, ExecutionStatus.ERROR, "endTs");
      } else {
        NotifyCallback callback =
            new StateMachineResumeCallback(stateExecutionInstance.getAppId(), stateExecutionInstance.getUuid());
        waitNotifyEngine.waitForAll(callback,
            executionResponse.getCorrelationIds().toArray(new String[executionResponse.getCorrelationIds().size()]));
      }

      handleSpawningStateExecutionInstances(sm, stateExecutionInstance, executionResponse);

    } else {
      if (executionResponse.getExecutionStatus() == ExecutionStatus.SUCCESS) {
        return successTransition(context);
      } else {
        return failedTransition(context, null);
      }
    }
    return null;
  }

  private StateExecutionInstance handleExecuteResponseException(ExecutionContextImpl context, Exception exception) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    StateMachine sm = context.getStateMachine();
    State currentState = sm.getState(stateExecutionInstance.getStateName());
    logger.info("Error seen in the state execution  - currentState : {}, stateExecutionInstanceId: {}", currentState,
        stateExecutionInstance.getUuid(), exception);
    try {
      return failedTransition(context, exception);
    } catch (Exception e2) {
      logger.error("Error in transitioning to failure state", e2);
    }
    return null;
  }

  private void handleSpawningStateExecutionInstances(
      StateMachine sm, StateExecutionInstance stateExecutionInstance, ExecutionResponse executionResponse) {
    if (executionResponse instanceof SpawningExecutionResponse) {
      SpawningExecutionResponse spawningExecutionResponse = (SpawningExecutionResponse) executionResponse;
      if (spawningExecutionResponse.getStateExecutionInstanceList() != null
          && spawningExecutionResponse.getStateExecutionInstanceList().size() > 0) {
        for (StateExecutionInstance executionInstance : spawningExecutionResponse.getStateExecutionInstanceList()) {
          executionInstance.setAppId(stateExecutionInstance.getAppId());
          executionInstance.setExecutionUuid(stateExecutionInstance.getExecutionUuid());
          execute(sm, executionInstance);
        }
      }
    }
  }

  private StateExecutionInstance successTransition(ExecutionContextImpl context) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    StateMachine sm = context.getStateMachine();

    updateStatus(stateExecutionInstance, ExecutionStatus.SUCCESS, "endTs");

    State nextState = sm.getSuccessTransition(stateExecutionInstance.getStateName());
    if (nextState == null) {
      logger.info("nextSuccessState is null.. ending execution  - currentState : "
          + stateExecutionInstance.getStateName() + ", stateExecutionInstanceId: " + stateExecutionInstance.getUuid());
      if (stateExecutionInstance.getNotifyId() == null) {
        logger.info("State Machine execution ended for the stateMachine: {}, executionUuid: {}", sm.getName(),
            stateExecutionInstance.getExecutionUuid());
        if (stateExecutionInstance.getCallback() != null) {
          stateExecutionInstance.getCallback().callback(context, ExecutionStatus.SUCCESS, null);
        } else {
          logger.info("No callback for the stateMachine: {}, executionUuid: {}", sm.getName(),
              stateExecutionInstance.getExecutionUuid());
        }
      } else {
        waitNotifyEngine.notify(stateExecutionInstance.getNotifyId(), ExecutionStatus.SUCCESS);
      }
    } else {
      StateExecutionInstance cloned = JsonUtils.clone(stateExecutionInstance, StateExecutionInstance.class);
      cloned.setUuid(null);
      cloned.setStateName(nextState.getName());
      return execute(sm, cloned);
    }

    return null;
  }

  private StateExecutionInstance failedTransition(ExecutionContextImpl context, Exception exception) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    StateMachine sm = context.getStateMachine();

    updateStatus(stateExecutionInstance, ExecutionStatus.FAILED, "endTs");

    State nextState = sm.getFailureTransition(stateExecutionInstance.getStateName());
    if (nextState == null) {
      logger.info("nextFailureState is null.. ending execution  - currentState : "
          + stateExecutionInstance.getStateName() + ", stateExecutionInstanceId: " + stateExecutionInstance.getUuid());
      if (stateExecutionInstance.getNotifyId() == null) {
        logger.info("State Machine execution failed for the stateMachine: {}, executionUuid: {}", sm.getName(),
            stateExecutionInstance.getExecutionUuid());
        if (stateExecutionInstance.getCallback() != null) {
          stateExecutionInstance.getCallback().callback(context, ExecutionStatus.SUCCESS, exception);
        } else {
          logger.info("No callback for the stateMachine: {}, executionUuid: {}", sm.getName(),
              stateExecutionInstance.getExecutionUuid());
        }
      } else {
        waitNotifyEngine.notify(stateExecutionInstance.getNotifyId(), ExecutionStatus.FAILED);
      }
    } else {
      StateExecutionInstance cloned = JsonUtils.clone(stateExecutionInstance, StateExecutionInstance.class);
      cloned.setUuid(null);
      cloned.setStateName(nextState.getName());
      return execute(sm, cloned);
    }
    return null;
  }

  private void updateStatus(StateExecutionInstance stateExecutionInstance, ExecutionStatus status, String tsField) {
    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);
    ops.set("status", status);
    ops.set(tsField, System.currentTimeMillis());

    wingsPersistence.update(stateExecutionInstance, ops);
  }

  private void updateStateExecutionData(
      StateExecutionInstance stateExecutionInstance, StateExecutionData stateExecutionData) {
    if (stateExecutionData == null) {
      return;
    }
    Map<String, StateExecutionData> stateExecutionMap = stateExecutionInstance.getStateExecutionMap();
    stateExecutionMap.put(stateExecutionInstance.getStateName(), stateExecutionData);
    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);
    ops.set("stateExecutionMap", stateExecutionMap);
    wingsPersistence.update(stateExecutionInstance, ops);
  }

  private static class SmExecutionDispatcher implements Runnable {
    private ExecutionContextImpl context;
    private StateMachineExecutor stateMachineExecutor;

    /**
     * @param context
     * @param stateMachineExecutor
     */
    public SmExecutionDispatcher(ExecutionContextImpl context, StateMachineExecutor stateMachineExecutor) {
      this.context = context;
      this.stateMachineExecutor = stateMachineExecutor;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
      stateMachineExecutor.startExecution(context);
    }
  }
}
