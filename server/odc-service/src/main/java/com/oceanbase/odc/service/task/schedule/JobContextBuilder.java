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

import java.util.Map;

import com.oceanbase.odc.service.task.caller.JobContext;

/**
 * @author yaobin
 * @date 2023-11-30
 * @since 4.2.4
 */
public interface JobContextBuilder {

    JobContext build(JobIdentity identity);

    JobContext build(JobIdentity identity, Map<String, Object> taskData);
}
