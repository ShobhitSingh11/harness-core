/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.GoogleCloudStorageArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("GoogleCloudStorageArtifactOutcome")
@JsonTypeName("GoogleCloudStorageArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.GoogleCloudStorageArtifactOutcome")
public class GoogleCloudStorageArtifactOutcome implements ArtifactOutcome {
  /** Google Cloud Storage connector. */
  String connectorRef;

  /** project */
  String project;

  /** artifactPath */
  String artifactPath;

  /** Bucket */
  String bucket;

  /** Identifier for artifact. */
  String identifier;

  /** Artifact type. */
  String type;

  /** Whether this config corresponds to primary artifact.*/
  boolean primaryArtifact;
  @Override
  public ArtifactSummary getArtifactSummary() {
    return GoogleCloudStorageArtifactSummary.builder().bucket(bucket).artifactPath(artifactPath).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }

  @Override
  public String getTag() {
    return artifactPath;
  }
}