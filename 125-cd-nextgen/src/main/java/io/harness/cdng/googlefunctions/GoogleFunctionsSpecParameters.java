/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.googlefunctions.command.GoogleFunctionsCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

@OwnedBy(HarnessTeam.CDP)
public interface GoogleFunctionsSpecParameters extends SpecParameters {
  @JsonIgnore ParameterField<List<TaskSelectorYaml>> getDelegateSelectors();

  @Nonnull
  @JsonIgnore
  default List<String> getCommandUnits() {
    return Arrays.asList(GoogleFunctionsCommandUnitConstants.fetchManifests.toString(),
        GoogleFunctionsCommandUnitConstants.prepareRollbackData.toString(),
        GoogleFunctionsCommandUnitConstants.deploy.toString());
  }
}