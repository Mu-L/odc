/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.service.resource.k8s.status;

import com.oceanbase.odc.service.resource.k8s.model.K8sResource;

/**
 * {@link K8sResourceMatcher}
 *
 * @author yh263208
 * @date 2024-09-12 15:27
 * @since ODC_release_4.3.2
 */
public interface K8sResourceMatcher<T extends K8sResource> {

    boolean matches(T k8sResource);

}
