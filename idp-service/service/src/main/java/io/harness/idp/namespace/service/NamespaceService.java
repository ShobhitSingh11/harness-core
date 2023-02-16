/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.service;

import io.harness.idp.namespace.beans.dto.Namespace;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;

import java.util.Optional;

public interface NamespaceService {
  Optional<Namespace> getNamespaceForAccountIdentifier(String accountIdentifier);

  Optional<Namespace> getAccountIdForNamespace(String namespace);
  NamespaceEntity saveAccountIdNamespace(String accountIdentifier);
}