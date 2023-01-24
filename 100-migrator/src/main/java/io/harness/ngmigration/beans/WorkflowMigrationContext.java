/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;

import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowMigrationContext {
  private Map<String, String> expressions;
  private Map<CgEntityId, CgEntityNode> entities;
  private Map<CgEntityId, NGYamlFile> migratedEntities;
  private Workflow workflow;

  public static WorkflowMigrationContext newInstance(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, Workflow workflow) {
    return WorkflowMigrationContext.builder()
        .workflow(workflow)
        .entities(entities)
        .migratedEntities(migratedEntities)
        .expressions(new HashMap<>())
        .build();
  }
}