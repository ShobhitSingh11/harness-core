/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(CDP)
public enum PlanType {
  @JsonProperty(TerraformCloudConstants.APPLY) APPLY(TerraformCloudConstants.APPLY),
  @JsonProperty(TerraformCloudConstants.DESTROY) DESTROY(TerraformCloudConstants.DESTROY);

  final String displayName;

  PlanType(String displayName) {
    this.displayName = displayName;
  }
}