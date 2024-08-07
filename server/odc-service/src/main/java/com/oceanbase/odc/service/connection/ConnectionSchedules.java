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
package com.oceanbase.odc.service.connection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.oceanbase.odc.service.common.ConditionOnServer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionOnServer
public class ConnectionSchedules {

    @Autowired
    private ConnectionSessionHistoryService connectionSessionHistoryService;
    @Autowired
    private ConnectionConfigRecycleService connectionConfigRecycleService;

    @Scheduled(fixedDelayString = "${odc.connect.session.history-update-interval-millis:120000}")
    public void refreshLastAccessTime() {
        connectionSessionHistoryService.refreshAllSessionHistory();
    }

    @Scheduled(fixedDelayString = "${odc.connect.temp.expire-check-interval-millis:600000}")
    public void clearInactiveTempConnectionConfigs() {
        connectionConfigRecycleService.clearInactiveTempConnectionConfigs();
    }

}
