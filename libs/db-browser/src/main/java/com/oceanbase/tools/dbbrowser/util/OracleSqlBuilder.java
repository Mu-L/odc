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
package com.oceanbase.tools.dbbrowser.util;

/**
 * @author jingtian
 */
public class OracleSqlBuilder extends SqlBuilder {
    private final StringBuilder sb;

    public OracleSqlBuilder() {
        this.sb = new StringBuilder();
    }

    @Override
    public SqlBuilder identifier(String identifier) {
        return append(StringUtils.quoteOracleIdentifier(identifier));
    }

    @Override
    public SqlBuilder value(String value) {
        return append(StringUtils.quoteOracleValue(value));
    }

    @Override
    public SqlBuilder defaultValue(String value) {
        return append(value);
    }

    @Override
    public SqlBuilder like(String fieldKey, String fieldLikeValue) {
        super.like(fieldKey, fieldLikeValue);
        return append(" ESCAPE ").value("\\");
    }
}
