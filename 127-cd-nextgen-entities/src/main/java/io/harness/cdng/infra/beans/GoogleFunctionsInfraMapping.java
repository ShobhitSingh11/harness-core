/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("googleFunctionsInfraMapping")
@JsonTypeName("googleFunctionsInfraMapping")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.infra.beans.GoogleFunctionsInfraMapping")
public class GoogleFunctionsInfraMapping implements InfraMapping {
  @Id private String uuid;
  private String accountId;
  private String gcpConnector;
  private String project;
  private String region;
}