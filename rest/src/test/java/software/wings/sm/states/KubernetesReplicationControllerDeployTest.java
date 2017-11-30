package software.wings.sm.states;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.reflection.Whitebox.setInternalState;
import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.api.ContainerServiceData.ContainerServiceDataBuilder.aContainerServiceData;
import static software.wings.api.ContainerServiceElement.ContainerServiceElementBuilder.aContainerServiceElement;
import static software.wings.api.PhaseElement.PhaseElementBuilder.aPhaseElement;
import static software.wings.api.ServiceElement.Builder.aServiceElement;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.ResizeStrategy.RESIZE_NEW_FIRST;
import static software.wings.beans.Service.Builder.aService;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.WorkflowExecution.WorkflowExecutionBuilder.aWorkflowExecution;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.CommandExecutionResult.Builder.aCommandExecutionResult;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.common.UUIDGenerator.getUuid;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.sm.states.KubernetesReplicationControllerDeploy.KubernetesReplicationControllerDeployBuilder.aKubernetesReplicationControllerDeploy;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.STATE_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mongodb.morphia.Key;
import software.wings.WingsBaseTest;
import software.wings.api.CommandStateExecutionData;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.DelegateTask;
import software.wings.beans.Environment;
import software.wings.beans.ErrorCode;
import software.wings.beans.GcpConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.CommandExecutionResult.CommandExecutionStatus;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.ServiceCommand;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.exception.WingsException;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionStatus;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.waitnotify.NotifyResponseData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by brett on 3/10/17
 */
public class KubernetesReplicationControllerDeployTest extends WingsBaseTest {
  private static final String KUBERNETES_REPLICATION_CONTROLLER_NAME = "kubernetes-rc-name.1";
  private static final String KUBERNETES_REPLICATION_CONTROLLER_OLD_NAME = "kubernetes-rc-name.0";

  @Mock private SettingsService settingsService;
  @Mock private DelegateService delegateService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ActivityService activityService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private SecretManager secretManager;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private DelegateProxyFactory delegateProxyFactory;

  @InjectMocks
  private KubernetesReplicationControllerDeploy kubernetesReplicationControllerDeploy =
      aKubernetesReplicationControllerDeploy(STATE_NAME).withCommandName(COMMAND_NAME).withInstanceCount(1).build();

  @Mock private ContainerService containerService;

  private WorkflowStandardParams workflowStandardParams = aWorkflowStandardParams()
                                                              .withAppId(APP_ID)
                                                              .withEnvId(ENV_ID)
                                                              .withArtifactIds(Lists.newArrayList(ARTIFACT_ID))
                                                              .build();
  private ServiceElement serviceElement = aServiceElement().withUuid(SERVICE_ID).withName(SERVICE_NAME).build();
  private PhaseElement phaseElement = aPhaseElement()
                                          .withUuid(getUuid())
                                          .withServiceElement(serviceElement)
                                          .withInfraMappingId(INFRA_MAPPING_ID)
                                          .withDeploymentType(DeploymentType.KUBERNETES.name())
                                          .build();
  private StateExecutionInstance stateExecutionInstance =
      aStateExecutionInstance()
          .withStateName(STATE_NAME)
          .addContextElement(workflowStandardParams)
          .addContextElement(phaseElement)
          .addContextElement(aContainerServiceElement()
                                 .withUuid(serviceElement.getUuid())
                                 .withMaxInstances(10)
                                 .withClusterName(CLUSTER_NAME)
                                 .withNamespace("default")
                                 .withName(KUBERNETES_REPLICATION_CONTROLLER_NAME)
                                 .withResizeStrategy(RESIZE_NEW_FIRST)
                                 .withInfraMappingId(INFRA_MAPPING_ID)
                                 .withDeploymentType(DeploymentType.KUBERNETES)
                                 .build())
          .addStateExecutionData(new PhaseStepExecutionData())
          .build();

  private Application app = anApplication().withUuid(APP_ID).withName(APP_NAME).build();
  private Environment env = anEnvironment().withAppId(APP_ID).withUuid(ENV_ID).withName(ENV_NAME).build();
  private Service service = aService().withAppId(APP_ID).withUuid(SERVICE_ID).withName(SERVICE_NAME).build();
  private SettingAttribute computeProvider =
      aSettingAttribute()
          .withValue(GcpConfig.builder().serviceAccountKeyFileContent("keyFileContent".toCharArray()).build())
          .build();
  private ExecutionContextImpl context;

  /**
   * Set up.
   */
  @Before
  public void setup() {
    when(appService.get(APP_ID)).thenReturn(app);
    when(serviceResourceService.get(APP_ID, SERVICE_ID)).thenReturn(service);
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(env);

    ServiceCommand serviceCommand =
        aServiceCommand()
            .withCommand(aCommand().withCommandType(CommandType.RESIZE).withName(COMMAND_NAME).build())
            .build();
    when(serviceResourceService.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, COMMAND_NAME)).thenReturn(serviceCommand);
    on(workflowStandardParams).set("appService", appService);
    on(workflowStandardParams).set("environmentService", environmentService);

    InfrastructureMapping infrastructureMapping = aGcpKubernetesInfrastructureMapping()
                                                      .withClusterName(CLUSTER_NAME)
                                                      .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                      .build();
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infrastructureMapping);

    Activity activity = Activity.builder().build();
    activity.setUuid(ACTIVITY_ID);
    when(activityService.save(any(Activity.class))).thenReturn(activity);

    when(settingsService.get(COMPUTE_PROVIDER_ID)).thenReturn(computeProvider);

    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));

    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null)).thenReturn(emptyList());
    when(secretManager.getEncryptionDetails(anyObject(), anyString(), anyString())).thenReturn(Collections.emptyList());
    setInternalState(kubernetesReplicationControllerDeploy, "secretManager", secretManager);
    when(workflowExecutionService.getExecutionDetails(anyString(), anyString()))
        .thenReturn(aWorkflowExecution().build());
    context = new ExecutionContextImpl(stateExecutionInstance);

    when(delegateProxyFactory.get(eq(ContainerService.class), any(DelegateTask.SyncTaskContext.class)))
        .thenReturn(containerService);
    when(containerService.getServiceDesiredCount(any(ContainerServiceParams.class))).thenReturn(Optional.of(0));
  }

  @Test
  public void shouldExecute() {
    on(context).set("serviceTemplateService", serviceTemplateService);

    ExecutionResponse response = kubernetesReplicationControllerDeploy.execute(context);
    assertThat(response).isNotNull().hasFieldOrPropertyWithValue("async", true);
    assertThat(response).isNotNull().hasFieldOrPropertyWithValue("async", true);
    assertThat(response.getCorrelationIds()).isNotNull().hasSize(1).contains(ACTIVITY_ID);
    verify(activityService).save(any(Activity.class));
    verify(delegateService).queueTask(any(DelegateTask.class));
  }

  @Test
  public void shouldExecuteThrowInvalidRequest() {
    when(containerService.getServiceDesiredCount(any(ContainerServiceParams.class))).thenReturn(Optional.empty());
    try {
      on(context).set("serviceTemplateService", serviceTemplateService);
      kubernetesReplicationControllerDeploy.execute(context);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.INVALID_REQUEST.getCode());
      assertThat(exception.getParams()).hasSize(1).containsKey("message");
      assertThat(exception.getParams().get("message")).asString().contains("Service setup not done, service name:");
    }
  }

  @Test
  public void shouldExecuteAsync() {
    Map<String, NotifyResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put("key", aCommandExecutionResult().withStatus(CommandExecutionStatus.SUCCESS).build());

    stateExecutionInstance.getStateExecutionMap().put(
        stateExecutionInstance.getStateName(), aCommandStateExecutionData().build());

    ExecutionResponse response = kubernetesReplicationControllerDeploy.handleAsyncResponse(context, notifyResponse);
    assertThat(response)
        .isNotNull()
        .hasFieldOrPropertyWithValue("async", false)
        .hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.SUCCESS);
  }

  @Test
  public void shouldExecuteAsyncWithOldReplicationController() {
    Map<String, NotifyResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put("key", aCommandExecutionResult().withStatus(CommandExecutionStatus.SUCCESS).build());

    CommandStateExecutionData commandStateExecutionData =
        aCommandStateExecutionData()
            .withActivityId(ACTIVITY_ID)
            .withNewInstanceData(singletonList(aContainerServiceData()
                                                   .withName(KUBERNETES_REPLICATION_CONTROLLER_NAME)
                                                   .withPreviousCount(0)
                                                   .withDesiredCount(1)
                                                   .build()))
            .withOldInstanceData(singletonList(aContainerServiceData()
                                                   .withName(KUBERNETES_REPLICATION_CONTROLLER_OLD_NAME)
                                                   .withPreviousCount(1)
                                                   .withDesiredCount(0)
                                                   .build()))
            .withDownsize(false)
            .build();
    stateExecutionInstance.getStateExecutionMap().put(stateExecutionInstance.getStateName(), commandStateExecutionData);

    ExecutionResponse response = kubernetesReplicationControllerDeploy.handleAsyncResponse(context, notifyResponse);
    assertThat(response).isNotNull().hasFieldOrPropertyWithValue("async", true);
    assertThat(response.getCorrelationIds()).isNotNull().hasSize(1).contains(ACTIVITY_ID);
    assertThat(response.getStateExecutionData()).isNotNull().isEqualTo(commandStateExecutionData);
    verify(delegateService).queueTask(any(DelegateTask.class));
  }

  @Test
  public void shouldExecuteAsyncWithOldReplicationControllerWithNoInstance() {
    Map<String, NotifyResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put("key", aCommandExecutionResult().withStatus(CommandExecutionStatus.SUCCESS).build());
    stateExecutionInstance.getStateExecutionMap().put(
        stateExecutionInstance.getStateName(), aCommandStateExecutionData().build());

    ExecutionResponse response = kubernetesReplicationControllerDeploy.handleAsyncResponse(context, notifyResponse);
    assertThat(response)
        .isNotNull()
        .hasFieldOrPropertyWithValue("async", false)
        .hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.SUCCESS);
  }
}
