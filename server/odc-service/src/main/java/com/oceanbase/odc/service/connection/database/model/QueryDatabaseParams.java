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
package com.oceanbase.odc.service.connection.database.model;

import java.util.List;

import com.oceanbase.odc.core.shared.constant.ConnectType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: Lebie
 * @Date: 2023/6/5 15:33
 * @Description: []
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryDatabaseParams {
    private String schemaName;

    private List<DatabaseType> types;

    private List<ConnectType> connectTypes;

    private Long projectId;

    private Long dataSourceId;

    private Long environmentId;

    private Boolean existed;

    private Boolean containsUnassigned;

    private Boolean includesPermittedAction;

    private String clusterName;

    private String tenantName;

    private String dataSourceName;
}
