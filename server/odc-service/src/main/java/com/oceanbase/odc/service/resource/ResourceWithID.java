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
package com.oceanbase.odc.service.resource;

import com.oceanbase.odc.metadb.resource.ResourceEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * @author longpeng.zlp
 * @date 2024/9/5 14:29
 */
@AllArgsConstructor
@Getter
@ToString
public class ResourceWithID<T extends Resource> {
    /**
     * unique seq equals to {@link ResourceEntity#getId()}
     */
    private final Long id;

    /**
     * resource to return
     */
    private final T resource;
}
