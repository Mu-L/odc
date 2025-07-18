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
package com.oceanbase.odc.service.permission.database.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.oceanbase.odc.core.flow.model.TaskParameters;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.connection.database.model.DatabaseType;
import com.oceanbase.odc.service.permission.project.ApplyProjectParameter.ApplyProject;
import com.oceanbase.odc.service.task.base.precheck.PreCheckRiskLevel;

import lombok.Data;

/**
 * @author gaoda.xy
 * @date 2024/1/3 13:54
 */
@Data
public class ApplyDatabaseParameter implements Serializable, TaskParameters {

    private static final long serialVersionUID = -2482302525012272875L;

    /**
     * Project to be applied for, required
     */
    private ApplyProject project;
    /**
     * Databases to be applied for, required
     */
    private List<ApplyDatabase> databases;
    /**
     * Permission types to be applied for, required
     */
    private List<DatabasePermissionType> types;
    /**
     * This field represents the duration of the permission. It is only used for the front end to
     * re-initiate tasks, and the backend does not rely on it
     */
    private String validDuration;
    /**
     * Expiration time, null means no expiration, optional
     */
    private Date expireTime;
    /**
     * Reason for application, required
     */
    private String applyReason;

    /**
     * Risk level info, required
     */
    private PreCheckRiskLevel riskLevel;

    @Data
    public static class ApplyDatabase implements Serializable {

        private static final long serialVersionUID = -8433967513537417701L;

        private Long id;
        private DatabaseType type;
        private String name;
        private Long dataSourceId;
        private String dataSourceName;
        private DialectType dialectType;

    }

}
