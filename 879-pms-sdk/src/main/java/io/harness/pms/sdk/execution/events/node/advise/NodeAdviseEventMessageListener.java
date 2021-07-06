package io.harness.pms.sdk.execution.events.node.advise;

import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_EXECUTOR_NAME;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;
import io.harness.pms.sdk.core.execution.events.node.advise.NodeAdviseEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class NodeAdviseEventMessageListener extends PmsAbstractMessageListener<AdviseEvent, NodeAdviseEventHandler> {
  @Inject
  public NodeAdviseEventMessageListener(@Named(SDK_SERVICE_NAME) String serviceName,
      NodeAdviseEventHandler nodeAdviseEventHandler, @Named(SDK_EXECUTOR_NAME) ExecutorService executorService) {
    super(serviceName, AdviseEvent.class, nodeAdviseEventHandler, executorService);
  }
}
