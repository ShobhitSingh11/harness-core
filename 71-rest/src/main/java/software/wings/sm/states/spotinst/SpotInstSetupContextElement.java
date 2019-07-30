package software.wings.sm.states.spotinst;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.spotinst.response.SpotInstSetupTaskResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.wings.beans.ResizeStrategy;
import software.wings.service.impl.spotinst.SpotInstCommandRequest;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpotInstSetupContextElement implements ContextElement {
  private String uuid;
  private String name;
  private String commandName;
  private Integer maxInstanceCount;
  private boolean useCurrentRunningInstanceCount;
  private Integer currentRunningInstanceCount;
  private ResizeStrategy resizeStrategy;
  private boolean isBlueGreen;
  private SpotInstCommandRequest commandRequest;
  private SpotInstSetupTaskResponse spotInstSetupTaskResponse;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.SPOTINST_SERVICE_SETUP;
  }

  @Override
  public Map<String, Object> paramMap(ExecutionContext context) {
    return null;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }
}
