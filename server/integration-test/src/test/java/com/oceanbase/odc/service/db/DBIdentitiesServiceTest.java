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
package com.oceanbase.odc.service.db;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.ServiceTestEnv;
import com.oceanbase.odc.TestConnectionUtil;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionConstants;
import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.service.db.model.ObjectIdentity;
import com.oceanbase.odc.service.db.model.SchemaIdentities;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

/**
 * @description:
 * @author: zijia.cj
 * @date: 2024/9/10 09:20
 * @since: 4.3.3
 */
public class DBIdentitiesServiceTest extends ServiceTestEnv {
    @Autowired
    private DBIdentitiesService dbIdentitiesService;

    private final static List<String> TABLE_NAME_LIST = Arrays.asList("test_table_1", "test_table_2", "test_table_3");
    private final static List<String> VIEW_NAME_LIST = Arrays.asList("test_view_1", "test_view_2", "test_view_3");
    private final static String SCHEMA_NAME = "db_identities_schema";

    @Before
    public void setUp() {
        ConnectionSession session = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("DROP DATABASE IF EXISTS ").append(SCHEMA_NAME).append(";");
        tableBuilder.append("CREATE DATABASE ").append(SCHEMA_NAME).append(";");
        for (String name : TABLE_NAME_LIST) {
            tableBuilder.append(String.format("CREATE TABLE IF NOT EXISTS %s (\n"
                    + "  id BIGINT NOT NULL AUTO_INCREMENT,\n"
                    + "  PRIMARY key (`id`)\n"
                    + ");", SCHEMA_NAME + "." + name));
        }
        session.getSyncJdbcExecutor(ConnectionSessionConstants.CONSOLE_DS_KEY).execute(tableBuilder.toString());
        StringBuilder viewBuilder = new StringBuilder();
        for (int i = 0; i < VIEW_NAME_LIST.size(); i++) {
            viewBuilder.append(
                    String.format("CREATE VIEW %s AS SELECT * FROM %s;", SCHEMA_NAME + "." + VIEW_NAME_LIST.get(i),
                            SCHEMA_NAME + "." + TABLE_NAME_LIST.get(i)));
        }
        session.getSyncJdbcExecutor(ConnectionSessionConstants.CONSOLE_DS_KEY).execute(viewBuilder.toString());
    }

    @After
    public void clear() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        JdbcOperations jdbcOperations =
                connectionSession.getSyncJdbcExecutor(ConnectionSessionConstants.CONSOLE_DS_KEY);
        StringBuilder viewBuilder = new StringBuilder();
        for (String name : VIEW_NAME_LIST) {
            viewBuilder.append(String.format("drop view %s;", SCHEMA_NAME + "." + name));
        }
        jdbcOperations.execute(viewBuilder.toString());
        StringBuilder tableBuilder = new StringBuilder();
        for (String name : TABLE_NAME_LIST) {
            tableBuilder.append(String.format("drop table %s;", SCHEMA_NAME + "." + name));
        }
        tableBuilder.append("DROP DATABASE IF EXISTS ").append(SCHEMA_NAME).append(";");
        jdbcOperations.execute(tableBuilder.toString());
    }

    @Test
    public void list_whenTypesIsEmpty_getEmptyList() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, Collections.emptyList());
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void list_whenTypesOnlyContainsTable_success() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<DBObjectType> types = Collections.singletonList(DBObjectType.TABLE);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, types);
        Assert.assertNotNull(result);
        Map<String, List<ObjectIdentity>> schema2Identities = result.stream().collect(
                Collectors.toMap(SchemaIdentities::getSchemaName, SchemaIdentities::getIdentities));
        List<String> nameList = schema2Identities.get(SCHEMA_NAME).stream()
                .filter(x -> DBObjectType.TABLE.equals(x.getType())).map(ObjectIdentity::getName).collect(
                        Collectors.toList());
        Assert.assertTrue(nameList.containsAll(TABLE_NAME_LIST));
    }

    @Test
    public void list_whenTypesOnlyContainsView_success() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<DBObjectType> types = Collections.singletonList(DBObjectType.VIEW);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, types);
        Assert.assertNotNull(result);
        Map<String, List<ObjectIdentity>> schema2Identities = result.stream().collect(
                Collectors.toMap(SchemaIdentities::getSchemaName, SchemaIdentities::getIdentities));
        List<String> nameList = schema2Identities.get(SCHEMA_NAME).stream()
                .filter(x -> DBObjectType.VIEW.equals(x.getType())).map(ObjectIdentity::getName).collect(
                        Collectors.toList());
        Assert.assertTrue(nameList.containsAll(VIEW_NAME_LIST));
    }

    @Test
    public void list_whenSchemaNotExists_success() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<DBObjectType> types = Arrays.asList(DBObjectType.TABLE, DBObjectType.VIEW);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, "UNKNOWN_SCHEMA", types);
        Assert.assertFalse(result.stream().anyMatch(r -> "UNKNOWN_SCHEMA".equals(r.getSchemaName())));
        Assert.assertFalse(result.isEmpty());
    }

    @Test
    public void list_whenSchemaExactlyExists_success() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<DBObjectType> types = Arrays.asList(DBObjectType.TABLE, DBObjectType.VIEW);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, SCHEMA_NAME, types);
        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue(result.stream().filter(r -> SCHEMA_NAME.equals(r.getSchemaName()))
                .allMatch(r -> CollectionUtils.isNotEmpty(r.getIdentities())));
    }

    @Test
    public void list_whenSchemaNotExistsAndTableOrViewExists_success() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<DBObjectType> types = Arrays.asList(DBObjectType.TABLE, DBObjectType.VIEW);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, "UNKNOWN_SCHEMA", "test_", types);
        Assert.assertFalse(result.stream().anyMatch(r -> "UNKNOWN_SCHEMA".equals(r.getSchemaName())));
        Assert.assertFalse(result.isEmpty());
    }

    @Test
    public void list_whenSchemaExactlyExistsAndTableOrViewExists_success() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<DBObjectType> types = Arrays.asList(DBObjectType.TABLE, DBObjectType.VIEW);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, SCHEMA_NAME, "test_", types);
        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue(result.stream().filter(r -> SCHEMA_NAME.equals(r.getSchemaName()))
                .filter(r -> r.getIdentities().stream().allMatch(rr -> rr.getName().contains("test_")))
                .allMatch(r -> CollectionUtils.isNotEmpty(r.getIdentities())));
    }

    @Test
    public void list_whenNoSchemaAndTableOrViewExists_success() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<DBObjectType> types = Arrays.asList(DBObjectType.TABLE, DBObjectType.VIEW);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, null, "test_", types);
        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue(result.stream().filter(r -> SCHEMA_NAME.equals(r.getSchemaName()))
                .filter(r -> r.getIdentities().stream().allMatch(rr -> rr.getName().contains("test_")))
                .allMatch(r -> CollectionUtils.isNotEmpty(r.getIdentities())));
    }

    @Test
    public void list_whenSchemaExactlyExistsAndTableOrViewNotExists_success() {
        ConnectionSession connectionSession = TestConnectionUtil.getTestConnectionSession(ConnectType.OB_MYSQL);
        List<DBObjectType> types = Arrays.asList(DBObjectType.TABLE, DBObjectType.VIEW);
        List<SchemaIdentities> result = dbIdentitiesService.list(connectionSession, SCHEMA_NAME, "_test_", types);
        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue(result.stream().filter(r -> SCHEMA_NAME.equals(r.getSchemaName()))
                .allMatch(r -> CollectionUtils.isEmpty(r.getIdentities())));
    }
}
