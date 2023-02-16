/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.secret.beans.entity.EnvironmentSecretEntity;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class EnvironmentSecretMapper {
  public EnvironmentSecret toDTO(EnvironmentSecretEntity environmentSecretEntity) {
    EnvironmentSecret secret = new EnvironmentSecret();
    secret.setName(environmentSecretEntity.getName());
    secret.setSecretIdentifier(environmentSecretEntity.getSecretIdentifier());
    secret.setCreated(environmentSecretEntity.getCreatedAt());
    secret.setUpdated(environmentSecretEntity.getLastModifiedAt());
    return secret;
  }

  public EnvironmentSecretEntity fromDTO(EnvironmentSecret secret) {
    return EnvironmentSecretEntity.builder()
        .name(secret.getName())
        .secretIdentifier(secret.getSecretIdentifier())
        .build();
  }
}