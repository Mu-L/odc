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
package com.oceanbase.odc.server.web.controller.v2;

import java.util.List;

import org.apache.commons.collections.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.service.common.response.ListResponse;
import com.oceanbase.odc.service.common.response.Responses;
import com.oceanbase.odc.service.common.util.SidUtils;
import com.oceanbase.odc.service.db.DBIdentitiesService;
import com.oceanbase.odc.service.db.model.SchemaIdentities;
import com.oceanbase.odc.service.session.ConnectSessionService;
import com.oceanbase.odc.service.state.model.StateName;
import com.oceanbase.odc.service.state.model.StatefulRoute;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

@RestController
@RequestMapping("api/v2/connect/sessions")
public class DBMetadataController {

    @Autowired
    private DBIdentitiesService identitiesService;
    @Autowired
    private ConnectSessionService sessionService;

    @GetMapping(value = {"/{sessionId}/metadata/identities"})
    @StatefulRoute(stateName = StateName.DB_SESSION, stateIdExpression = "#sessionId")
    public ListResponse<SchemaIdentities> listIdentities(@PathVariable String sessionId,
            @RequestParam(required = false, name = "type") List<DBObjectType> types,
            @RequestParam(required = false, name = "schemaName") String schemaName,
            @RequestParam(required = false, name = "identityNameLike") String identityNameLike) {
        ConnectionSession session = sessionService.nullSafeGet(SidUtils.getSessionId(sessionId), true);
        if (ConnectionSessionUtil.isLogicalSession(session)) {
            return Responses.list(ListUtils.EMPTY_LIST);
        }
        return Responses
                .list(identitiesService.list(session, schemaName, identityNameLike, types));
    }

}
