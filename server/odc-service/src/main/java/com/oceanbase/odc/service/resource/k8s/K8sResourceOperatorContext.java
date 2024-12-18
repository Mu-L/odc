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
package com.oceanbase.odc.service.resource.k8s;

import java.util.function.Function;

import com.oceanbase.odc.service.resource.ResourceID;
import com.oceanbase.odc.service.resource.k8s.client.K8sJobClientSelector;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author longpeng.zlp
 * @date 2024/9/2 17:24
 */
@AllArgsConstructor
@Data
public class K8sResourceOperatorContext {
    private final K8sJobClientSelector k8sJobClientSelector;
    private final Function<ResourceID, Long> createElapsedTimeFunc;
    private final long podPendingTimeoutSeconds;
}