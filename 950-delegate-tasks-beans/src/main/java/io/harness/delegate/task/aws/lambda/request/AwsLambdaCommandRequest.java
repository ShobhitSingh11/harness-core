/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aws.lambda.AwsLambdaCommandTypeNG;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotEmpty;

@OwnedBy(CDP)
public interface AwsLambdaCommandRequest extends TaskParameters, ExecutionCapabilityDemander {
  @NotEmpty AwsLambdaCommandTypeNG getAwsLambdaCommandType();

  String getCommandName();

  CommandUnitsProgress getCommandUnitsProgress();

  AwsLambdaFunctionsInfraConfig getAwsLambdaFunctionsInfraConfig();

  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = getAwsLambdaFunctionsInfraConfig();
    List<ExecutionCapability> capabilities = new ArrayList<>();
    capabilities.add((ExecutionCapability) AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
        awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), maskingEvaluator));
    return capabilities;
  }
}