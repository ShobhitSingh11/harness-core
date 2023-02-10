/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.logging.AutoLogContext;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(PIPELINE)
public class UnAckRequestLogContext extends AutoLogContext {
  public UnAckRequestLogContext(UnAckRequest unAckRequest) {
    super(buildLogContext(unAckRequest), OverrideBehavior.OVERRIDE_NESTS);
  }

  private static Map<String, String> buildLogContext(UnAckRequest unAckRequest) {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("topic", unAckRequest.getTopic());
    contextMap.put("subTopic", unAckRequest.getSubTopic());
    contextMap.put("itemId", unAckRequest.getItemID());
    return contextMap;
  }
}