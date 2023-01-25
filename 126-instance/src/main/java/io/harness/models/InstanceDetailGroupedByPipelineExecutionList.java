/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.models;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstanceDetailGroupedByPipelineExecutionList {
  @NotNull List<InstanceDetailGroupedByPipelineExecution> instanceDetailGroupedByPipelineExecutionList;

  @Value
  @Builder
  public static class InstanceDetailGroupedByPipelineExecution {
    @NotNull private String pipelineId;
    @NotNull private String planExecutionId;
    private long lastDeployedAt;
    @NotNull List<InstanceDetailsDTO> instances;
  }
}