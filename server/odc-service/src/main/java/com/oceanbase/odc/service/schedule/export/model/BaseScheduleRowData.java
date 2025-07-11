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
package com.oceanbase.odc.service.schedule.export.model;

import java.util.UUID;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oceanbase.odc.service.exporter.model.Encryptable;
import com.oceanbase.odc.service.schedule.model.ScheduleType;
import com.oceanbase.odc.service.schedule.model.TriggerConfig;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseScheduleRowData implements Encryptable {

    private String rowId = UUID.randomUUID().toString();

    private String originScheduleId;

    private String originProjectName;

    @NotNull
    private ExportedDatabase database;

    @Nullable
    private ExportedDatabase targetDatabase;

    @NotBlank
    private String name;

    @NotNull
    private ScheduleType type;

    private TriggerConfig triggerConfig;

    private String description;


    @Override
    public void encrypt(String encryptKey) {
        if (database != null) {
            database.encrypt(encryptKey);
        }
        if (targetDatabase != null) {
            targetDatabase.encrypt(encryptKey);
        }
    }

    @Override
    public void decrypt(String encryptKey) {
        if (database != null) {
            database.decrypt(encryptKey);
        }
        if (targetDatabase != null) {
            targetDatabase.decrypt(encryptKey);
        }
    }

    public ScheduleRowPreviewDto preview() {
        ScheduleRowPreviewDto rowPreviewDto = new ScheduleRowPreviewDto();
        rowPreviewDto.setType(type);
        rowPreviewDto.setRowId(rowId);
        rowPreviewDto.setOriginId(originScheduleId);
        rowPreviewDto.setOriginProjectName(originProjectName);
        rowPreviewDto.setDatabase(database);
        rowPreviewDto.setTargetDatabase(targetDatabase);
        rowPreviewDto.setDescription(description);
        return rowPreviewDto;
    }
}
