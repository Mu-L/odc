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
package com.oceanbase.odc.service.sqlcheck.factory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.sqlcheck.SqlCheckRule;
import com.oceanbase.odc.service.sqlcheck.SqlCheckRuleContext;
import com.oceanbase.odc.service.sqlcheck.SqlCheckRuleFactory;
import com.oceanbase.odc.service.sqlcheck.model.SqlCheckRuleType;
import com.oceanbase.odc.service.sqlcheck.rule.MySQLRestrictIndexDataTypes;
import com.oceanbase.odc.service.sqlcheck.rule.OracleRestrictIndexDataTypes;

import lombok.NonNull;

public class RestrictIndexDataTypesFactory implements SqlCheckRuleFactory {

    private final JdbcOperations jdbc;

    public RestrictIndexDataTypesFactory(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SqlCheckRuleType getSupportsType() {
        return SqlCheckRuleType.RESTRICT_INDEX_DATATYPES;
    }

    @Override
    @SuppressWarnings("all")
    public SqlCheckRule generate(@NonNull SqlCheckRuleContext sqlCheckRuleContext) {
        DialectType dialectType = sqlCheckRuleContext.getDialectType();
        Map<String, Object> parameters = sqlCheckRuleContext.getParameters();
        String key = getParameterNameKey("allowed-datatypes");
        Set<String> types;
        if (parameters == null || parameters.isEmpty() || parameters.get(key) == null) {
            types = new HashSet<>(Arrays.asList("int", "varchar2", "number", "float", "bigint"));
        } else {
            types = new HashSet<>((List<String>) parameters.get(key));
        }
        return (dialectType.isMysql() || dialectType.isDoris()) ? new MySQLRestrictIndexDataTypes(jdbc, types)
                : new OracleRestrictIndexDataTypes(jdbc, types);
    }

}
