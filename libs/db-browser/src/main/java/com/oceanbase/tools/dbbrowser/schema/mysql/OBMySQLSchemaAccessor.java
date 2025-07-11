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
package com.oceanbase.tools.dbbrowser.schema.mysql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBIndexAlgorithm;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshParameter;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecord;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecordParam;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedViewRefreshMethod;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBObjectWarningDescriptor;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.parser.SqlParser;
import com.oceanbase.tools.dbbrowser.parser.result.ParseSqlResult;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessorSqlMappers;
import com.oceanbase.tools.dbbrowser.schema.constant.Statements;
import com.oceanbase.tools.dbbrowser.schema.constant.StatementsFiles;
import com.oceanbase.tools.dbbrowser.util.DBSchemaAccessorUtil;
import com.oceanbase.tools.dbbrowser.util.MySQLSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;
import com.oceanbase.tools.sqlparser.statement.Statement;
import com.oceanbase.tools.sqlparser.statement.createmview.CreateMaterializedView;
import com.oceanbase.tools.sqlparser.statement.createtable.CreateTable;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * applicable to OB [4.3.5.2, ~)
 *
 * @author jingtian
 */
@Slf4j
public class OBMySQLSchemaAccessor extends MySQLNoLessThan5700SchemaAccessor {

    protected static final Set<String> ESCAPE_SCHEMA_SET = new HashSet<>(4);

    static {
        ESCAPE_SCHEMA_SET.add("PUBLIC");
        ESCAPE_SCHEMA_SET.add("LBACSYS");
        ESCAPE_SCHEMA_SET.add("ORAAUDITOR");
        ESCAPE_SCHEMA_SET.add("__public");
    }

    public OBMySQLSchemaAccessor(JdbcOperations jdbcOperations) {
        super(jdbcOperations);
        this.sqlMapper = DBSchemaAccessorSqlMappers.get(StatementsFiles.OBMYSQL_432x);
    }

    @Override
    public List<DBObjectIdentity> listMViews(String schemaName) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("select MVIEW_NAME FROM OCEANBASE.DBA_MVIEWS WHERE OWNER = ").value(schemaName);
        return jdbcOperations.query(sb.toString(),
                (rs, rowNum) -> DBObjectIdentity.of(schemaName, DBObjectType.MATERIALIZED_VIEW, rs.getString(1)));
    }

    @Override
    public List<DBObjectIdentity> listAllMViewsLike(String mViewNameLike) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append(
                "select OWNER AS schema_name, MVIEW_NAME AS name,'MATERIALIZED_VIEW' AS type FROM OCEANBASE.DBA_MVIEWS");
        if (StringUtils.isNotBlank(mViewNameLike)) {
            sb.append(" WHERE ").like("MVIEW_NAME", mViewNameLike);
        }
        sb.append(" ORDER BY name ASC;");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public Boolean refreshMVData(DBMViewRefreshParameter parameter) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("call DBMS_MVIEW.REFRESH('").append(parameter.getDatabaseName()).append(".")
                .append(parameter.getMvName()).append("'");
        if (Objects.nonNull(parameter.getRefreshMethod())) {
            sb.append(",").value(parameter.getRefreshMethod().getValue());
        }
        if (Objects.nonNull(parameter.getParallelismDegree())) {
            sb.append(",").append("refresh_parallel => ").append(parameter.getParallelismDegree() + "");
        }
        sb.append(");");
        jdbcOperations.execute(sb.toString());
        return Boolean.TRUE;
    }

    @Override
    public List<DBMViewRefreshRecord> listMViewRefreshRecords(DBMViewRefreshRecordParam param) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("SELECT ")
                .value(param.getSchemaName())
                .append(" as mv_owner, ")
                .value(param.getMvName())
                .append(" as mv_name,REFRESH_ID as refresh_id,REFRESH_METHOD as refresh_method,REFRESH_OPTIMIZATIONS as refresh_optimizations,ADDITIONAL_EXECUTIONS as additional_executions,START_TIME as start_time, END_TIME as end_time,ELAPSED_TIME as elapsed_time,LOG_SETUP_TIME as log_setup_time,LOG_PURGE_TIME as log_purge_time,INITIAL_NUM_ROWS as initial_num_rows,FINAL_NUM_ROWS as final_num_rows FROM oceanbase.DBA_MVREF_STATS where MV_OWNER =")
                .value(param.getSchemaName())
                .append(" AND MV_NAME = ")
                .value(param.getMvName())
                .append(" ORDER BY REFRESH_ID DESC")
                .append(" LIMIT ")
                .append(param.getQueryLimit());
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBMViewRefreshRecord.class));
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        MySQLSqlBuilder getOptions = new MySQLSqlBuilder();
        getOptions.append(
                "SELECT REFRESH_METHOD,REWRITE_ENABLED,ON_QUERY_COMPUTATION,REFRESH_DOP,LAST_REFRESH_TYPE,LAST_REFRESH_DATE,LAST_REFRESH_END_TIME FROM OCEANBASE.DBA_MVIEWS WHERE OWNER = ")
                .value(schemaName).append(" AND MVIEW_NAME = ").value(mViewName);

        DBMaterializedView mView = new DBMaterializedView();
        mView.setName(mViewName);
        mView.setSchemaName(schemaName);
        jdbcOperations.query(getOptions.toString(), (rs) -> {
            mView.setRefreshMethod(DBMaterializedViewRefreshMethod.getEnumByShowName(rs.getString("REFRESH_METHOD")));
            mView.setEnableQueryRewrite(rs.getBoolean("REWRITE_ENABLED"));
            mView.setEnableQueryComputation(rs.getBoolean("ON_QUERY_COMPUTATION"));
            mView.setParallelismDegree(rs.getLong("REFRESH_DOP"));
            mView.setLastRefreshType(
                    DBMaterializedViewRefreshMethod.getEnumByShowName(rs.getString("LAST_REFRESH_TYPE")));
            mView.setLastRefreshStartTime(rs.getTimestamp("LAST_REFRESH_DATE"));
            mView.setLastRefreshEndTime(rs.getTimestamp("LAST_REFRESH_END_TIME"));
        });
        return mView;
    }

    @Override
    public List<DBTableConstraint> listMViewConstraints(String schemaName, String mViewName) {
        return listTableConstraints(schemaName, getContainerTable(schemaName, mViewName));
    }

    @Override
    public List<DBTableIndex> listMViewIndexes(String schemaName, String mViewName) {
        List<DBTableIndex> indexList = super.listTableIndexes(schemaName, mViewName);
        fillIndexInfo(indexList, schemaName, getContainerTable(schemaName, mViewName));
        for (DBTableIndex index : indexList) {
            if (index.getAlgorithm() == DBIndexAlgorithm.UNKNOWN) {
                index.setAlgorithm(DBIndexAlgorithm.BTREE);
            }
        }
        return indexList;
    }

    @Override
    public List<String> showDatabases() {
        return super.showDatabases().stream().filter(database -> !ESCAPE_SCHEMA_SET.contains(database))
                .collect(Collectors.toList());
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        DBDatabase database = new DBDatabase();
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append(
                "select object_name, timestamp from oceanbase.DBA_OBJECTS where object_type = 'DATABASE' and object_name = ")
                .value(schemaName);
        jdbcOperations.query(sb.toString(), rs -> {
            String objectName = rs.getString("object_name");
            String timestamp = rs.getString("timestamp");
            database.setName(objectName);
            database.setId(objectName + "_" + timestamp);

        });
        String sql =
                "SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.schemata where SCHEMA_NAME ='"
                        + schemaName + "'";
        jdbcOperations.query(sql, rs -> {
            database.setCharset(rs.getString("DEFAULT_CHARACTER_SET_NAME"));
            database.setCollation(rs.getString("DEFAULT_COLLATION_NAME"));
        });
        return database;
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        List<DBObjectIdentity> results = super.listTables(schemaName, tableNameLike);

        if (StringUtils.isBlank(schemaName) || "oceanbase".equals(schemaName)) {
            MySQLSqlBuilder querySystemTable = new MySQLSqlBuilder();
            querySystemTable.append("show full tables from oceanbase where Table_type='BASE TABLE'");
            if (StringUtils.isNotBlank(tableNameLike)) {
                querySystemTable.append(" and ").like("tables_in_oceanbase", tableNameLike);
            }
            try {
                List<String> tables =
                        jdbcOperations.query(querySystemTable.toString(), (rs, rowNum) -> rs.getString(1));
                tables.forEach(name -> results.add(DBObjectIdentity.of("oceanbase", DBObjectType.TABLE, name)));
            } catch (Exception e) {
                log.warn("List system tables from 'oceanbase' failed, reason={}", e.getMessage());
            }
        }

        if (StringUtils.isBlank(schemaName) || "mysql".equals(schemaName)) {
            MySQLSqlBuilder queryMysqlTable = new MySQLSqlBuilder();
            queryMysqlTable.append("show full tables from `mysql` where Table_type='BASE TABLE'");
            if (StringUtils.isNotBlank(tableNameLike)) {
                queryMysqlTable.append(" and ").like("tables_in_mysql", tableNameLike);
            }
            try {
                jdbcOperations.query(queryMysqlTable.toString(),
                        (rs, num) -> results.add(DBObjectIdentity.of("mysql", DBObjectType.TABLE, rs.getString(1))));
            } catch (Exception e) {
                log.warn("List base tables from 'mysql' failed, reason={}", e.getMessage());
            }
        }
        return results;
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        List<DBObjectIdentity> results = super.listAllSystemViews(viewNameLike);
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("show full tables from `oceanbase` where Table_type='SYSTEM VIEW'");
        if (StringUtils.isNotBlank(viewNameLike)) {
            sb.append(" AND ").like("Tables_in_oceanbase", viewNameLike);
        }
        try {
            List<String> oceanbaseViews = jdbcOperations.query(sb.toString(), (rs, rowNum) -> rs.getString(1));
            oceanbaseViews.forEach(name -> results.add(DBObjectIdentity.of("oceanbase", DBObjectType.VIEW, name)));
        } catch (Exception ex) {
            log.info("List tables for 'oceanbase' failed, reason={}", ex.getMessage());
        }

        return results;
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        List<DBTableColumn> columns = super.listTableColumns(schemaName, tableName);
        setStoredColumnByDDL(schemaName, tableName, columns);
        return columns;
    }

    protected void setStoredColumnByDDL(String schemeName, String tableName, List<DBTableColumn> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        try {
            MySQLSqlBuilder sb = new MySQLSqlBuilder();
            sb.append("show create table ");
            sb.schemaPrefixIfNotBlank(schemeName);
            sb.identifier(tableName);
            List<String> ddl =
                    jdbcOperations.query(sb.toString(), (rs, num) -> rs.getString(2));
            if (CollectionUtils.isEmpty(ddl) || StringUtils.isBlank(ddl.get(0))) {
                fillWarning(columns, DBObjectType.COLUMN, "get table DDL failed");
            } else {
                ParseSqlResult result = SqlParser.parseMysql(ddl.get(0));
                if (CollectionUtils.isEmpty(result.getColumns())) {
                    fillWarning(columns, DBObjectType.COLUMN, "parse DDL failed, may view object");
                } else {
                    columns.forEach(column -> result.getColumns().forEach(columnDefinition -> {
                        if (StringUtils.equals(column.getName(), columnDefinition.getName())) {
                            column.setStored(columnDefinition.getIsStored());
                        }
                    }));
                }
            }
        } catch (Exception e) {
            fillWarning(columns, DBObjectType.COLUMN, "query ddl failed");
            log.warn("Fetch table ddl for parsing column failed", e);
        }
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        List<DBTableIndex> indexList = super.listTableIndexes(schemaName, tableName);
        fillIndexInfo(indexList, schemaName, tableName);
        for (DBTableIndex index : indexList) {
            if (index.getAlgorithm() == DBIndexAlgorithm.UNKNOWN) {
                index.setAlgorithm(DBIndexAlgorithm.BTREE);
            }
        }
        return indexList;
    }

    @Override
    protected void handleIndexAvailability(DBTableIndex index, String availability) {
        if ("available".equals(availability)) {
            index.setAvailable(true);
        } else if ("unavailable".equals(availability)) {
            index.setAvailable(false);
        }
    }

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        Map<String, List<DBTableIndex>> tableName2Indexes = super.listTableIndexes(schemaName);
        for (Map.Entry<String, List<DBTableIndex>> entry : tableName2Indexes.entrySet()) {
            fillIndexInfo(entry.getValue(), schemaName, entry.getKey());
            for (DBTableIndex index : entry.getValue()) {
                if (index.getAlgorithm() == DBIndexAlgorithm.UNKNOWN) {
                    index.setAlgorithm(DBIndexAlgorithm.BTREE);
                }
            }
        }
        return tableName2Indexes;
    }

    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName, Map<String, String> tableName2Ddl) {
        Map<String, List<DBTableIndex>> tableName2Indexes = super.listTableIndexes(schemaName);
        tableName2Indexes.keySet().forEach(tableName -> {
            if (tableName2Ddl.containsKey(tableName)) {
                parseDdlToSetIndexInfo(tableName2Ddl.get(tableName), tableName2Indexes.get(tableName));
            } else {
                fillIndexInfo(tableName2Indexes.get(tableName), schemaName, tableName);
            }
        });
        return tableName2Indexes;
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        return listTableColumnGroups(getTableDDL(schemaName, tableName));
    }

    private List<DBColumnGroupElement> listTableColumnGroups(String ddl) {
        Statement statement = SqlParser.parseMysqlStatement(ddl);
        if (statement instanceof CreateTable) {
            CreateTable stmt = (CreateTable) statement;
            return stmt.getColumnGroupElements() == null ? Collections.emptyList()
                    : stmt.getColumnGroupElements().stream()
                            .map(DBColumnGroupElement::ofColumnGroupElement).collect(Collectors.toList());
        }
        if (statement instanceof CreateMaterializedView) {
            CreateMaterializedView stmt = (CreateMaterializedView) statement;
            return stmt.getColumnGroupElements() == null ? Collections.emptyList()
                    : stmt.getColumnGroupElements().stream()
                            .map(DBColumnGroupElement::ofColumnGroupElement).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    protected boolean isIndexDistinguishesVisibility() {
        return true;
    }

    protected void fillIndexInfo(List<DBTableIndex> indexList, String schemaName,
            String tableName) {
        setIndexInfoByDDL(indexList, schemaName, tableName);
    }

    protected void setIndexInfoByDDL(List<DBTableIndex> indexList, String schemaName, String tableName) {
        try {
            MySQLSqlBuilder sb = new MySQLSqlBuilder();
            sb.append("show create table ");
            sb.identifier(schemaName, tableName);
            // Column label May 'Create Table' or 'Create View', use columnIndex here
            List<String> ddl =
                    jdbcOperations.query(sb.toString(), (rs, num) -> rs.getString(2));
            if (CollectionUtils.isEmpty(ddl) || StringUtils.isBlank(ddl.get(0))) {
                fillWarning(indexList, DBObjectType.INDEX, "get index DDL failed");
            } else {
                parseDdlToSetIndexInfo(ddl.get(0), indexList);
            }
        } catch (Exception e) {
            fillWarning(indexList, DBObjectType.INDEX, "query index ddl failed");
            log.warn("Fetch table index through ddl parsing failed", e);
        }
    }

    private void parseDdlToSetIndexInfo(String ddl, List<DBTableIndex> indexList) {
        if (StringUtils.isBlank(ddl)) {
            fillWarning(indexList, DBObjectType.INDEX, "table ddl is blank, can not set index range by parse ddl");
            return;
        }
        try {
            ParseSqlResult result = SqlParser.parseMysql(ddl);
            if (CollectionUtils.isEmpty(result.getIndexes())) {
                fillWarning(indexList, DBObjectType.INDEX, "parse index DDL failed");
            } else {
                indexList.forEach(index -> result.getIndexes().forEach(dbIndex -> {
                    if (StringUtils.equals(index.getName(), dbIndex.getName())) {
                        index.setGlobal("GLOBAL".equalsIgnoreCase(dbIndex.getRange().name()));
                        index.setColumnGroups(dbIndex.getColumnGroups());
                    }
                }));
            }
        } catch (Exception e) {
            fillWarning(indexList, DBObjectType.INDEX, "failed to set index info by parse ddl");
            log.warn("failed to set index info by parse ddl:{}", ddl, e);
        }
    }

    protected <T extends DBObjectWarningDescriptor> void fillWarning(List<T> warningDescriptor, DBObjectType type,
            String reason) {
        if (CollectionUtils.isEmpty(warningDescriptor)) {
            return;
        }
        warningDescriptor
                .forEach(descriptor -> {
                    if (StringUtils.isBlank(descriptor.getWarning())) {
                        DBSchemaAccessorUtil.fillWarning(descriptor, type, reason);
                    }
                });
    }

    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("SHOW SEQUENCES IN ").identifier(schemaName);
        List<String> sequenceNames = jdbcOperations.queryForList(sb.toString(), String.class);
        return sequenceNames.stream().map(name -> DBObjectIdentity.of(schemaName, DBObjectType.SEQUENCE, name)).collect(
                Collectors.toList());
    }

    @Override
    public Map<String, DBTable> getTables(@NonNull String schemaName, List<String> tableNames) {
        // TODO: Only query the table information of tableNames passed upstream
        Map<String, DBTable> returnVal = new HashMap<>();
        tableNames = showTables(schemaName);
        if (tableNames.isEmpty()) {
            return returnVal;
        }
        Map<String, String> tableName2Ddl = new HashMap<>();
        tableNames.stream()
                .forEach(tableName -> tableName2Ddl.put(tableName, getTableDDL(schemaName, tableName)));
        Map<String, List<DBTableColumn>> tableName2Columns = listTableColumns(schemaName, Collections.emptyList());
        Map<String, List<DBTableIndex>> tableName2Indexes = listTableIndexes(schemaName, tableName2Ddl);
        Map<String, List<DBTableConstraint>> tableName2Constraints = listTableConstraints(schemaName);
        Map<String, DBTableOptions> tableName2Options = listTableOptions(schemaName);
        for (String tableName : tableNames) {
            if (!tableName2Columns.containsKey(tableName)) {
                continue;
            }
            DBTable table = new DBTable();
            table.setSchemaName(schemaName);
            table.setOwner(schemaName);
            table.setName(tableName);
            table.setColumns(tableName2Columns.getOrDefault(tableName, new ArrayList<>()));
            table.setIndexes(tableName2Indexes.getOrDefault(tableName, new ArrayList<>()));
            table.setConstraints(tableName2Constraints.getOrDefault(tableName, new ArrayList<>()));
            table.setTableOptions(tableName2Options.getOrDefault(tableName, new DBTableOptions()));
            table.setColumnGroups(listTableColumnGroups(tableName2Ddl.get(tableName)));
            try {
                table.setPartition(getPartition(schemaName, tableName));
            } catch (Exception e) {
                log.warn("Failed to set table partition", e);
            }
            table.setDDL(tableName2Ddl.get(tableName));
            returnVal.put(tableName, table);
        }
        return returnVal;
    }

    @Override
    public List<String> showExternalTables(String schemaName) {
        return showExternalTablesLike(schemaName, null);
    }


    @Override
    public List<String> showExternalTablesLike(String schemaName, String tableNameLike) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("SELECT table_name FROM information_schema.tables WHERE TABLE_TYPE = 'EXTERNAL TABLE'");
        if (StringUtils.isNotBlank(schemaName)) {
            sb.append(" AND table_schema=");
            sb.value(schemaName);
        }
        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" AND ").like("table_name", tableNameLike);
        }
        sb.append(" ORDER BY table_name");
        return jdbcOperations.queryForList(sb.toString(), String.class);
    }

    @Override
    public List<DBObjectIdentity> listExternalTables(String schemaName, String tableNameLike) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("select table_schema as schema_name, 'EXTERNAL_TABLE' as type, table_name as name ");
        sb.append("from information_schema.tables where table_type = 'EXTERNAL TABLE'");
        if (StringUtils.isNotBlank(schemaName)) {
            sb.append(" AND table_schema=");
            sb.value(schemaName);
        }
        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" AND ").like("table_name", tableNameLike);
        }
        sb.append(" ORDER BY schema_name, table_name");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public boolean isExternalTable(String schemaName, String tableName) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("SELECT table_type FROM information_schema.tables");
        if (StringUtils.isNotBlank(schemaName)) {
            sb.append(" Where table_schema=");
            sb.value(schemaName);
        }
        if (StringUtils.isNotBlank(tableName)) {
            sb.append(" AND table_name = ");
            sb.value(tableName);
        }
        String tableType = jdbcOperations.queryForObject(sb.toString(), String.class);
        if (tableType == null) {
            throw new IllegalArgumentException("table name: " + tableName + " is not exist");
        }
        if (StringUtils.equalsIgnoreCase(tableType, "EXTERNAL TABLE")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean syncExternalTableFiles(String schemaName, String tableName) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("ALTER EXTERNAL TABLE ").identifier(schemaName, tableName).append(" REFRESH");
        jdbcOperations.execute(sb.toString());
        return true;
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicExternalTableColumns(String schemaName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_SCHEMA_EXTERNAL_TABLE_COLUMNS);
        List<DBTableColumn> tableColumns = jdbcOperations.query(sql, new Object[] {schemaName, schemaName},
                listBasicTableColumnRowMapper());
        return tableColumns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    @Override
    public List<DBTableColumn> listBasicExternalTableColumns(String schemaName, String externalTableName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_EXTERNAL_TABLE_COLUMNS);
        return jdbcOperations.query(sql, new Object[] {schemaName, externalTableName}, listBasicTableColumnRowMapper());
    }


    @Override
    public Map<String, List<DBTableColumn>> listBasicMViewColumns(String schemaName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_SCHEMA_MATERIALIZED_VIEW_COLUMNS);
        List<DBTableColumn> tableColumns = jdbcOperations.query(sql, new Object[] {schemaName, schemaName},
                listBasicTableColumnRowMapper());
        return tableColumns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    public List<DBTableColumn> listBasicMViewColumns(String schemaName, String externalTableName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_MATERIALIZED_VIEW_COLUMNS);
        return jdbcOperations.query(sql, new Object[] {schemaName, externalTableName}, listBasicTableColumnRowMapper());
    }

    @Override
    protected void correctColumnPrecisionIfNeed(List<DBTableColumn> tableColumns) {}

    private String getContainerTable(String schemaName, String mViewName) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append("SELECT CONTAINER_NAME \n" +
                "FROM OCEANBASE.DBA_MVIEWS \n" +
                "WHERE OWNER = ")
                .value(schemaName)
                .append(" AND MVIEW_NAME = ")
                .value(mViewName);
        return jdbcOperations.queryForObject(sb.toString(), String.class);
    }

}
