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
package com.oceanbase.odc.plugin.task.obmysql.partitionplan.invoker.partitionname;

import java.sql.Connection;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import lombok.NonNull;

/**
 * {@link OBMySQLHistoricalPartitionNameGenerator}
 *
 * @author yh263208
 * @date 2024-03-22 10:25
 * @since ODC_release_4.2.4
 */
@Deprecated
public class OBMySQLHistoricalPartitionNameGenerator extends OBMySQLDateBasedPartitionNameGenerator {

    private static final String TARGET_FUNCTION_NAME = "UNIX_TIMESTAMP";

    @Override
    public String getName() {
        return "HISTORICAL_PARTITION_NAME_GENERATOR";
    }

    @Override
    protected Date getPartitionUpperBound(@NonNull Connection connection,
            @NonNull String partitionKey, @NonNull String upperBound, String namingSuffixExpression) {
        if (StringUtils.startsWith(partitionKey, TARGET_FUNCTION_NAME)) {
            return new Date(Long.parseLong(upperBound) * 1000);
        }
        return super.getPartitionUpperBound(connection, partitionKey, upperBound, namingSuffixExpression);
    }

}
