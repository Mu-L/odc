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
package com.oceanbase.tools.dbbrowser.stats.mysql;

import org.springframework.jdbc.core.JdbcOperations;

import lombok.NonNull;

/**
 * {@link OBMySQLStatsAccessor}
 *
 * @author yh263208
 * @date 2022-11-09 15:02
 * @since ODC_release_4.1.0
 */
public class OBMySQLStatsAccessor extends MySQLNoLessThan5700StatsAccessor {

    public OBMySQLStatsAccessor(@NonNull JdbcOperations jdbcOperations) {
        super(jdbcOperations);
    }

}
