/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import io.harness.k8s.model.K8sPod;

import java.util.Collections;
import java.util.List;

public interface K8sNGTaskResponse {
  default List<K8sPod> getPreviousK8sPodList() {
    return Collections.emptyList();
  }

  default List<K8sPod> getTotalK8sPodList() {
    return Collections.emptyList();
  }
}
