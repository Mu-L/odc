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
package com.oceanbase.odc.service.audit.util;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.oceanbase.odc.metadb.audit.AuditEventMetaEntity;
import com.oceanbase.odc.service.audit.model.AuditEventMeta;

@Mapper
public interface AuditEventMetaMapper {
    AuditEventMetaMapper INSTANCE = Mappers.getMapper(AuditEventMetaMapper.class);

    @Mapping(target = "actionName", expression = "java(entity.getAction().getLocalizedMessage())")
    @Mapping(target = "typeName", expression = "java(entity.getType().getLocalizedMessage())")
    AuditEventMeta entityToModel(AuditEventMetaEntity entity);

    AuditEventMetaEntity modelToEntity(AuditEventMeta model);
}
