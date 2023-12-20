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

package com.oceanbase.odc.service.task.schedule;

import java.util.Collections;
import java.util.List;

import com.oceanbase.odc.service.task.config.TaskFrameworkProperties;

/**
 * @author yaobin
 * @date 2023-12-15
 * @since 4.2.4
 */
public class FixedHostUrlProvider implements HostUrlProvider {

    private final TaskFrameworkProperties taskFrameworkProperties;

    public FixedHostUrlProvider(TaskFrameworkProperties taskFrameworkProperties) {
        this.taskFrameworkProperties = taskFrameworkProperties;
    }

    @Override
    public List<String> hostUrl() {
        return Collections.singletonList(taskFrameworkProperties.getOdcUrl());
    }
}