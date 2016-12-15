package software.wings.delegate.service;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static software.wings.beans.Delegate.Builder.aDelegate;
import static software.wings.beans.DelegateTaskResponse.Builder.aDelegateTaskResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.Delegate;
import software.wings.beans.Delegate.Status;
import software.wings.beans.DelegateTask;
import software.wings.beans.RestResponse;
import software.wings.delegate.app.DelegateConfiguration;
import software.wings.dl.PageResponse;
import software.wings.managerclient.ManagerClient;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * Created by peeyushaggarwal on 11/29/16.
 */
public class DelegateServiceImpl implements DelegateService {
  private final Logger logger = LoggerFactory.getLogger(DelegateServiceImpl.class);

  @Inject private DelegateConfiguration delegateConfiguration;

  @Inject private ManagerClient managerClient;

  @Inject private ScheduledExecutorService scheduledExecutorService;

  @Inject private ExecutorService executorService;

  @Override
  public void run() {
    try {
      String ip = InetAddress.getLocalHost().getHostAddress();
      String hostName = InetAddress.getLocalHost().getHostName();
      String accountId = delegateConfiguration.getAccountId();
      Delegate.Builder builder =
          aDelegate().withIp(ip).withAccountId(accountId).withHostName(hostName).withVersion(getVersion());

      logger.info("Registering delegate....");
      RestResponse<Delegate> delegateResponse =
          managerClient
              .registerDelegate(accountId,
                  builder.but().withLastHeartBeat(System.currentTimeMillis()).withStatus(Status.ENABLED).build())
              .execute()
              .body();

      builder.withUuid(delegateResponse.getResource().getUuid()).withStatus(delegateResponse.getResource().getStatus());
      logger.info("Delegate registered with id " + delegateResponse.getResource().getUuid());

      String delegateId = delegateResponse.getResource().getUuid();
      logger.info("Starting heartbeat at interval {} ms", delegateConfiguration.getHeartbeatIntervalMs());
      scheduledExecutorService.scheduleAtFixedRate(() -> {
        logger.debug("sending heartbeat..");
        try {
          managerClient
              .sendHeartbeat(delegateId, accountId, builder.but().withLastHeartBeat(System.currentTimeMillis()).build())
              .execute();
        } catch (IOException e) {
          logger.error("Exception while sending heartbeat ", e);
        }
      }, 0, delegateConfiguration.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);

      logger.info("Delegate started.");
      while (true) {
        RestResponse<PageResponse<DelegateTask>> delegateTasks =
            managerClient.getTasks(delegateId, accountId).execute().body();
        if (isNotEmpty(delegateTasks.getResource())) {
          DelegateTask delegateTask = delegateTasks.getResource().get(0);
          executorService
              .submit(delegateTask.getTaskType().getDelegateRunnableTask(delegateTask.getUuid(),
                  delegateTask.getParameters(),
                  notifyResponseData -> {
                    try {
                      managerClient
                          .sendTaskStatus(delegateId, delegateTask.getUuid(), accountId,
                              aDelegateTaskResponse()
                                  .withTaskId(delegateTask.getUuid())
                                  .withAccountId(accountId)
                                  .withResponse(notifyResponseData)
                                  .build())
                          .execute();
                    } catch (IOException e) {
                      e.printStackTrace();
                    }
                  }))
              .get();
        } else {
          // Loop for tasks.
          Thread.sleep(1000);
        }
      }

    } catch (Exception e) {
      logger.error("Exception while starting/running delegate ", e);
    }
  }

  private String getVersion() {
    return System.getProperty("version", "1.0.0-DEV");
  }
}
