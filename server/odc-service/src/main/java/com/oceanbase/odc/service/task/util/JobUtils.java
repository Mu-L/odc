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

package com.oceanbase.odc.service.task.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.Gson;
import com.oceanbase.odc.common.util.SystemUtils;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.task.constants.JobConstants;
import com.oceanbase.odc.service.task.constants.JobEnvConstants;
import com.oceanbase.odc.service.task.schedule.JobIdentity;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yaobin
 * @date 2023-11-15
 * @since 4.2.4
 */
@Slf4j
public class JobUtils {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public static String generateJobName(JobIdentity ji) {
        return JobConstants.TEMPLATE_JOB_NAME_PREFIX + ji.getId() + "-" + LocalDateTime.now().format(DTF);
    }

    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        // todo replace by jackson ConnectionConfig serialize ignore by @JsonProperty(value = "password",
        // access = Access.WRITE_ONLY)
        return new Gson().toJson(obj);
    }

    public static int getPort() {
        String port = SystemUtils.getEnvOrProperty(JobEnvConstants.ODC_SERVER_PORT);
        return port != null ? Integer.parseInt(port) : 8989;
    }

    public static ConnectionConfig getMetaDBConnectionConfig() {
        ConnectionConfig config = new ConnectionConfig();
        config.setHost(SystemUtils.getEnvOrProperty("DATABASE_HOST"));
        String port = SystemUtils.getEnvOrProperty("DATABASE_PORT");
        config.setPort(port != null ? Integer.parseInt(port) : 8989);
        config.setDefaultSchema(SystemUtils.getEnvOrProperty("DATABASE_NAME"));
        config.setName(SystemUtils.getEnvOrProperty("DATABASE_USERNAME"));
        config.setPassword(SystemUtils.getEnvOrProperty("DATABASE_PASSWORD"));
        config.setId(1L);
        return config;
    }


}