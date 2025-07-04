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
package com.oceanbase.odc.service.db.model;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description:
 * @author: zijia.cj
 * @date: 2025/4/2 13:53
 * @since: 4.3.4
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GenerateUpdateMViewDDLReq {
    @NotNull
    private DBMaterializedView previous;
    @NotNull
    private DBMaterializedView current;
}
