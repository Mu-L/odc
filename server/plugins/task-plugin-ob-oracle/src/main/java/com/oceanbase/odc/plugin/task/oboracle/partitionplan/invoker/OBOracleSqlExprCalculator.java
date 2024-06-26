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
package com.oceanbase.odc.plugin.task.oboracle.partitionplan.invoker;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import com.oceanbase.odc.plugin.task.obmysql.partitionplan.invoker.OBMySQLExprCalculator;
import com.oceanbase.odc.plugin.task.obmysql.partitionplan.mapper.CellDataProcessor;
import com.oceanbase.odc.plugin.task.oboracle.partitionplan.datatype.OBOracleJdbcDataTypeFactory;
import com.oceanbase.odc.plugin.task.oboracle.partitionplan.mapper.CellDataProcessors;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;
import com.oceanbase.tools.dbbrowser.util.OracleSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

import lombok.NonNull;

/**
 * {@link OBOracleSqlExprCalculator}
 *
 * @author yh263208
 * @date 2024-01-26 15:43
 * @since ODC_release_4.2.4
 * @see OBMySQLExprCalculator
 */
public class OBOracleSqlExprCalculator extends OBMySQLExprCalculator {

    public OBOracleSqlExprCalculator(@NonNull Connection connection) {
        super(connection);
    }

    @Override
    protected CellDataProcessor getCellDataProcessor(@NonNull DataType dataType) {
        return CellDataProcessors.getByDataType(dataType);
    }

    @Override
    protected DataType getDataType(@NonNull ResultSetMetaData metaData, Integer index) throws SQLException {
        return new OBOracleJdbcDataTypeFactory(metaData, index).generate();
    }

    @Override
    protected String generateExecuteSql(String expression) {
        SqlBuilder sqlBuilder = new OracleSqlBuilder();
        return sqlBuilder.append("SELECT ").append(expression).append(" FROM DUAL").toString();
    }

}
