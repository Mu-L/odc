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
package com.oceanbase.odc.service.session;

import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.oceanbase.odc.core.authority.util.SkipAuthorize;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionConstants;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.core.shared.PreConditions;
import com.oceanbase.odc.core.shared.Verify;
import com.oceanbase.odc.core.shared.constant.LimitMetric;
import com.oceanbase.odc.core.sql.split.SqlCommentProcessor;
import com.oceanbase.odc.service.config.OrganizationConfigUtils;
import com.oceanbase.odc.service.regulation.ruleset.SqlConsoleRuleService;
import com.oceanbase.odc.service.regulation.ruleset.model.SqlConsoleRules;
import com.oceanbase.odc.service.session.model.SessionSettings;

/**
 * Transaction service object, used to config some settings
 *
 * @author yh263208
 * @date 2021-06-08 11:32
 * @since ODC_release_2.4.2
 */
@Service
@Validated
@SkipAuthorize("inside connect session")
public class SessionSettingsService {

    @Autowired
    private SessionProperties sessionProperties;

    @Autowired
    private OrganizationConfigUtils organizationConfigUtils;

    @Autowired
    private SqlConsoleRuleService sqlConsoleRuleService;

    public SessionSettings getSessionSettings(@NotNull ConnectionSession session) {
        SessionSettings settings = new SessionSettings();
        Boolean autocommit = false;
        if (!ConnectionSessionUtil.isLogicalSession(session)) {
            JdbcOperations jdbcOperations = session.getSyncJdbcExecutor(ConnectionSessionConstants.CONSOLE_DS_KEY);
            autocommit = jdbcOperations.execute(Connection::getAutoCommit);
        }
        settings.setAutocommit(autocommit);
        settings.setObVersion(ConnectionSessionUtil.getVersion(session));
        settings.setDelimiter(ConnectionSessionUtil.getSqlCommentProcessor(session).getDelimiter());
        settings.setQueryLimit(ConnectionSessionUtil.getQueryLimit(session));
        Optional<Integer> envMaxQueryLimit = sqlConsoleRuleService.getProperties(
                ConnectionSessionUtil.getRuleSetId(session), SqlConsoleRules.MAX_QUERY_LIMIT,
                session.getDialectType(), Integer.class);
        settings.setMaxQueryLimit(envMaxQueryLimit.orElseGet(organizationConfigUtils::getDefaultMaxQueryLimit));
        return settings;
    }

    public SessionSettings setSessionSettings(@NotNull ConnectionSession session,
            @NotNull @Valid SessionSettings settings) {
        Integer wait2UpdateQueryLimit = settings.getQueryLimit();
        if (sessionProperties.getResultSetMaxRows() >= 0) {
            PreConditions.lessThanOrEqualTo("queryLimit", LimitMetric.TRANSACTION_QUERY_LIMIT,
                    wait2UpdateQueryLimit, sessionProperties.getResultSetMaxRows());
        }
        if (!ConnectionSessionUtil.isLogicalSession(session)) {
            JdbcOperations jdbcOperations = session.getSyncJdbcExecutor(ConnectionSessionConstants.CONSOLE_DS_KEY);
            Boolean autocommit = jdbcOperations.execute(Connection::getAutoCommit);
            if (!Objects.equals(autocommit, settings.getAutocommit())) {
                jdbcOperations.execute((ConnectionCallback<Void>) conn -> {
                    conn.setAutoCommit(settings.getAutocommit());
                    return null;
                });
            }
        }
        SqlCommentProcessor processor = ConnectionSessionUtil.getSqlCommentProcessor(session);
        if (!settings.getDelimiter().equals(processor.getDelimiter())) {
            processor.setDelimiter(settings.getDelimiter());
        }
        Integer queryLimit = ConnectionSessionUtil.getQueryLimit(session);

        if (!Objects.equals(wait2UpdateQueryLimit, queryLimit)) {
            Optional<Integer> envMaxQueryLimit = sqlConsoleRuleService.getProperties(
                    ConnectionSessionUtil.getRuleSetId(session), SqlConsoleRules.MAX_QUERY_LIMIT,
                    session.getDialectType(), Integer.class);
            Verify.notGreaterThan(wait2UpdateQueryLimit,
                    envMaxQueryLimit.orElseGet(organizationConfigUtils::getDefaultMaxQueryLimit),
                    "query limit value");
            ConnectionSessionUtil.setQueryLimit(session, wait2UpdateQueryLimit);
        }
        return settings;
    }

}
