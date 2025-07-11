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
package com.oceanbase.tools.dbbrowser.schema;

import static com.oceanbase.tools.dbbrowser.editor.DBObjectUtilsTest.loadAsString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.oceanbase.tools.dbbrowser.env.BaseTestEnv;
import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBIndexAlgorithm;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshParameter;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecord;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecordParam;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedViewRefreshMethod;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionType;
import com.oceanbase.tools.dbbrowser.model.DBVariable;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.util.DBSchemaAccessorUtil;
import com.oceanbase.tools.dbbrowser.util.DBSchemaAccessors;
import com.oceanbase.tools.dbbrowser.util.VersionUtils;
import com.oceanbase.tools.sqlparser.statement.createtable.ColumnAttributes;

import lombok.Data;

/**
 * TODO: all implementation classes of {@link DBSchemaAccessor} should have a separate test class to
 * test their methods, and the methods in their test class will only be triggered when the version
 * of the test data source matches their requirements.
 * 
 * @author jingtian
 */
public class OBMySQLSchemaAccessorTest extends BaseTestEnv {
    private static final String BASE_PATH = "src/test/resources/table/obmysql/";
    private static String ddl;
    private static String dropTables;
    private static String dropMVs;
    private static String testProcedureDDL;
    private static String testFunctionDDL;
    private static final List<DataType> verifyDataTypes = new ArrayList<>();
    private static final List<ColumnAttributes> columnAttributes = new ArrayList<>();
    private static final JdbcTemplate jdbcTemplate = new JdbcTemplate(getOBMySQLDataSource());
    private static final DBSchemaAccessors dbSchemaAccessors = new DBSchemaAccessors(getOBMySQLDataSource());
    private static final DBSchemaAccessor accessor = dbSchemaAccessors.createOBMysql();
    private static final boolean isSupportMaterializedView =
            VersionUtils.isGreaterThanOrEqualsTo(dbSchemaAccessors.getVersion(), "4.3.5.2");

    @BeforeClass
    public static void setUp() throws Exception {
        initVerifyDataTypes();
        initVerifyColumnAttributes();

        dropTables = loadAsString(BASE_PATH + "drop.sql");
        jdbcTemplate.execute(dropTables);

        if (isSupportMaterializedView) {
            dropMVs = loadAsString(BASE_PATH + "dropMV.sql");
            jdbcTemplate.execute(dropTables);
        }

        ddl = loadAsString(BASE_PATH + "testTableColumnDDL.sql", BASE_PATH + "testTableIndexDDL.sql",
                BASE_PATH + "testTableConstraintDDL.sql", BASE_PATH + "testPartitionDDL.sql",
                BASE_PATH + "testViewDDL.sql");
        jdbcTemplate.execute(ddl);

        testProcedureDDL = loadAsString(BASE_PATH + "testProcedureDDL.sql");
        batchExcuteSql(testProcedureDDL);
        testFunctionDDL = loadAsString(BASE_PATH + "testFunctionDDL.sql");
        batchExcuteSql(testFunctionDDL);

        if (isSupportMaterializedView) {
            String createMV = loadAsString(BASE_PATH + "testMVDDL.sql");
            jdbcTemplate.execute(createMV);
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        jdbcTemplate.execute(dropTables);
        if (isSupportMaterializedView) {
            jdbcTemplate.execute(dropMVs);
        }
    }

    private static void batchExcuteSql(String str) {
        for (String ddl : str.split("/")) {
            jdbcTemplate.execute(ddl);
        }
    }

    @Test
    public void listAllMViewsLike_InputIsEmptyString_Success() {
        if (isSupportMaterializedView) {
            List<DBObjectIdentity> dbObjectIdentities = accessor.listAllMViewsLike("");
            Assert.assertTrue(dbObjectIdentities.size() >= 9);
        }
    }

    @Test
    public void listAllMViewsLike_InputIsNonEmptyString_Success() {
        if (isSupportMaterializedView) {
            List<DBObjectIdentity> mViewsContains_ = accessor.listAllMViewsLike("_");
            Assert.assertTrue(mViewsContains_.size() > 0);
            Assert.assertTrue(mViewsContains_.stream().allMatch(o -> o.getName().contains("_")));
        }
    }

    @Test
    public void listMViews_Success() {
        if (isSupportMaterializedView) {
            List<DBObjectIdentity> dbObjectIdentities = accessor.listMViews(getOBMySQLDataBaseName());
            Assert.assertEquals(9, dbObjectIdentities.size());
        }
    }

    @Test
    public void refreshMVData_Success() {
        if (isSupportMaterializedView) {
            DBMViewRefreshParameter DBMViewRefreshParameter =
                    new DBMViewRefreshParameter(getOBMySQLDataBaseName(), "test_mv_all_syntax",
                            DBMaterializedViewRefreshMethod.REFRESH_COMPLETE, 2L);
            Boolean aBoolean = accessor.refreshMVData(DBMViewRefreshParameter);
            Assert.assertTrue(aBoolean);
        }
    }

    @Test
    public void getMView_Success() {
        if (isSupportMaterializedView) {
            DBMaterializedView test_mv_allSyntax = accessor.getMView(getOBMySQLDataBaseName(), "test_mv_all_syntax");
            Assert.assertEquals("test_mv_all_syntax", test_mv_allSyntax.getName());
            Assert.assertEquals(DBMaterializedViewRefreshMethod.REFRESH_COMPLETE,
                    test_mv_allSyntax.getRefreshMethod());
            Assert.assertFalse(test_mv_allSyntax.getEnableQueryRewrite());
            Assert.assertFalse(test_mv_allSyntax.getEnableQueryComputation());
            Assert.assertEquals(DBMaterializedViewRefreshMethod.REFRESH_COMPLETE,
                    test_mv_allSyntax.getLastRefreshType());
            Assert.assertNotNull(test_mv_allSyntax.getLastRefreshStartTime());
            Assert.assertNotNull(test_mv_allSyntax.getLastRefreshEndTime());

            DBMaterializedView test_mv_computation = accessor.getMView(getOBMySQLDataBaseName(), "test_mv_computation");
            Assert.assertTrue(test_mv_computation.getEnableQueryComputation());

            DBMaterializedView test_mv_queryRewrite =
                    accessor.getMView(getOBMySQLDataBaseName(), "test_mv_query_rewrite");
            Assert.assertTrue(test_mv_queryRewrite.getEnableQueryRewrite());

            DBMaterializedView test_mv_complete = accessor.getMView(getOBMySQLDataBaseName(), "test_mv_complete");
            Assert.assertEquals(DBMaterializedViewRefreshMethod.REFRESH_COMPLETE,
                    test_mv_complete.getRefreshMethod());

            DBMaterializedView test_mv_fast = accessor.getMView(getOBMySQLDataBaseName(), "test_mv_fast");
            Assert.assertEquals(DBMaterializedViewRefreshMethod.REFRESH_FAST, test_mv_fast.getRefreshMethod());

            DBMaterializedView test_mv_force = accessor.getMView(getOBMySQLDataBaseName(), "test_mv_force");
            Assert.assertEquals(DBMaterializedViewRefreshMethod.REFRESH_FORCE, test_mv_force.getRefreshMethod());

            DBMaterializedView test_mv_never = accessor.getMView(getOBMySQLDataBaseName(), "test_mv_never");
            Assert.assertEquals(DBMaterializedViewRefreshMethod.NEVER_REFRESH, test_mv_never.getRefreshMethod());

        }
    }

    @Test
    public void listTableColumnGroups_stmtIsCreateMaterializedView_Success() {
        if (isSupportMaterializedView) {
            List<DBColumnGroupElement> columnGroups =
                    accessor.listTableColumnGroups(getOBMySQLDataBaseName(), "test_mv_each_column");
            Assert.assertTrue(columnGroups.get(0).isEachColumn());
        }
    }

    @Test
    public void listMViewRefreshRecords_Success() {
        if (isSupportMaterializedView) {
            refreshMVData_Success();
            DBMViewRefreshRecordParam param =
                    new DBMViewRefreshRecordParam(getOBMySQLDataBaseName(), "test_mv_all_syntax", 1);
            List<DBMViewRefreshRecord> dbmViewRefreshRecords = accessor.listMViewRefreshRecords(param);
            Assert.assertEquals(getOBMySQLDataBaseName(), dbmViewRefreshRecords.get(0).getMvOwner());
            Assert.assertEquals("test_mv_all_syntax", dbmViewRefreshRecords.get(0).getMvName());
            Assert.assertEquals("COMPLETE",
                    dbmViewRefreshRecords.get(0).getRefreshMethod());
        }
    }

    @Test
    public void listBasicMViewColumns_InSchema_Success() {
        if (isSupportMaterializedView) {
            Map<String, List<DBTableColumn>> columns = accessor.listBasicMViewColumns(getOBMySQLDataBaseName());
            Assert.assertTrue(columns.containsKey("test_mv_all_syntax"));
            Assert.assertTrue(columns.get("test_mv_all_syntax").stream()
                    .allMatch(column -> column.getSchemaName().equals(getOBMySQLDataBaseName())));
            Assert.assertEquals(Arrays.asList("prim", "col2", "col3", "col4"), (columns.get("test_mv_all_syntax")
                    .stream().map(DBTableColumn::getName).collect(Collectors.toList())));

            Assert.assertTrue(columns.containsKey("test_mv_computation"));
            Assert.assertTrue(columns.get("test_mv_computation").stream()
                    .allMatch(column -> column.getSchemaName().equals(getOBMySQLDataBaseName())));
            Assert.assertEquals(Arrays.asList("col1", "count(*)"), (columns.get("test_mv_computation").stream()
                    .map(DBTableColumn::getName).collect(Collectors.toList())));
        }
    }

    @Test
    public void listBasicMViewColumns_InMView_Success() {
        if (isSupportMaterializedView) {
            List<DBTableColumn> columns =
                    accessor.listBasicMViewColumns(getOBMySQLDataBaseName(), "test_mv_all_syntax");
            Assert.assertEquals(4, columns.size());
            List<String> expect = Arrays.asList("prim", "col2", "col3", "col4");
            columns.forEach(column -> {
                Assert.assertTrue(expect.contains(column.getName()));
                Assert.assertEquals(column.getTableName(), "test_mv_all_syntax");
                Assert.assertEquals(column.getSchemaName(), getOBMySQLDataBaseName());
            });
        }
    }

    @Test
    public void listMViewConstraints_Success() {
        if (isSupportMaterializedView) {
            List<DBTableConstraint> constraints =
                    accessor.listMViewConstraints(getOBMySQLDataBaseName(), "test_mv_all_syntax");
            Assert.assertEquals(Collections.emptyList(), constraints);
        }
    }

    @Test
    public void listMViewIndexes_Success() {
        if (isSupportMaterializedView) {
            List<DBTableIndex> indexList = accessor.listMViewIndexes(getOBMySQLDataBaseName(), "test_mv_auto_refresh");

            Assert.assertEquals("test_global_idx", indexList.get(0).getName());
            Assert.assertEquals(getOBMySQLDataBaseName(), indexList.get(0).getSchemaName());

            Assert.assertEquals("test_local_idx", indexList.get(1).getName());
            Assert.assertEquals(getOBMySQLDataBaseName(), indexList.get(1).getSchemaName());

            Assert.assertEquals("test_eachcolumn_idx", indexList.get(2).getName());
            Assert.assertEquals(getOBMySQLDataBaseName(), indexList.get(2).getSchemaName());

            Assert.assertEquals("test_allcolumn_idx", indexList.get(3).getName());
            Assert.assertEquals(getOBMySQLDataBaseName(), indexList.get(3).getSchemaName());
        }
    }

    @Test
    public void listUsers_Success() {
        List<DBObjectIdentity> dbUsers = accessor.listUsers();
        Assert.assertFalse(dbUsers.isEmpty());
        Assert.assertSame(DBObjectType.USER, dbUsers.get(0).getType());
        Assert.assertNotNull(dbUsers.get(0).getName());
    }

    @Test
    public void listBasicSchemaColumns_TestAllColumnDataTypes_Success() {
        Map<String, List<DBTableColumn>> columns = accessor.listBasicTableColumns(getOBMySQLDataBaseName());
        Assert.assertTrue(columns.containsKey("test_data_type"));
        Assert.assertEquals(verifyDataTypes.size(), columns.get("test_data_type").size());
    }

    @Test
    public void listBasicTableColumns_TestAllColumnDataTypes_Success() {
        List<DBTableColumn> columns =
                accessor.listBasicTableColumns(getOBMySQLDataBaseName(), "test_data_type");
        Assert.assertEquals(columns.size(), verifyDataTypes.size());
        for (int i = 0; i < columns.size(); i++) {
            Assert.assertEquals(verifyDataTypes.get(i).getColumnName(), columns.get(i).getName());
            Assert.assertEquals(verifyDataTypes.get(i).getTypeName(), columns.get(i).getTypeName());
        }
    }

    @Test
    public void listBasicViewColumns_SchemaViewColumns_Success() {
        Map<String, List<DBTableColumn>> columns = accessor.listBasicViewColumns(getOBMySQLDataBaseName());
        Assert.assertTrue(columns.containsKey("view_test1"));
        Assert.assertTrue(columns.containsKey("view_test2"));
        Assert.assertEquals(2, columns.get("view_test1").size());
        Assert.assertEquals(1, columns.get("view_test2").size());
    }

    @Test
    public void listBasicViewColumns_ViewColumns_Success() {
        List<DBTableColumn> columns = accessor.listBasicViewColumns(getOBMySQLDataBaseName(), "view_test1");
        Assert.assertEquals(2, columns.size());
    }

    @Test
    public void listBasicColumnsInfo_Success() {
        Map<String, List<DBTableColumn>> columns = accessor.listBasicColumnsInfo(getOBMySQLDataBaseName());
        Assert.assertTrue(columns.containsKey("test_data_type"));
        Assert.assertEquals(verifyDataTypes.size(), columns.get("test_data_type").size());
        Assert.assertTrue(columns.containsKey("view_test1"));
        Assert.assertTrue(columns.containsKey("view_test2"));
        Assert.assertEquals(2, columns.get("view_test1").size());
        Assert.assertEquals(1, columns.get("view_test2").size());
    }

    @Test
    public void listTableColumns_TestAllColumnDataTypes_Success() {
        List<DBTableColumn> columns =
                accessor.listTableColumns(getOBMySQLDataBaseName(), "test_data_type");
        Assert.assertEquals(columns.size(), verifyDataTypes.size());
        for (int i = 0; i < columns.size(); i++) {
            Assert.assertEquals(verifyDataTypes.get(i).getColumnName(), columns.get(i).getName());
            Assert.assertEquals(verifyDataTypes.get(i).getTypeName(), columns.get(i).getTypeName());
            Assert.assertEquals(verifyDataTypes.get(i).getDisplayPrecision(), columns.get(i).getPrecision());
            Assert.assertEquals(verifyDataTypes.get(i).getDisplayScale(), columns.get(i).getScale());
        }
    }

    @Test
    public void listTableColumns_TestColumnAttributesOtherThanDataType_Success() {
        List<DBTableColumn> columns =
                accessor.listTableColumns(getOBMySQLDataBaseName(), "test_other_than_data_type");
        Assert.assertEquals(columns.size(), columnAttributes.size());
        for (int i = 0; i < columns.size(); i++) {
            Assert.assertEquals(columns.get(i).getName(), columnAttributes.get(i).getColumnName());
            Assert.assertEquals(columns.get(i).getNullable(), columnAttributes.get(i).getNullable());
            Assert.assertEquals(columns.get(i).getAutoIncrement(), columnAttributes.get(i).getAutoIncrement());
            Assert.assertEquals(columns.get(i).getVirtual(), columnAttributes.get(i).getVirtual());
            Assert.assertEquals(columns.get(i).getDefaultValue(), columnAttributes.get(i).getDefaultValue());
            Assert.assertEquals(columns.get(i).getComment(), columnAttributes.get(i).getComments());
        }
    }

    @Test
    public void listTableIndex_TestIndexType_Success() {
        List<DBTableIndex> indexList = accessor.listTableIndexes(getOBMySQLDataBaseName(), "test_index_type");
        Assert.assertEquals(2, indexList.size());
        Assert.assertEquals(DBIndexAlgorithm.BTREE, indexList.get(0).getAlgorithm());
        Assert.assertEquals(DBIndexAlgorithm.BTREE, indexList.get(1).getAlgorithm());
    }

    @Test
    public void listTableIndex_TestIndexRange_Success() {
        List<DBTableIndex> indexList = accessor.listTableIndexes(getOBMySQLDataBaseName(), "test_index_range");
        Assert.assertEquals(2, indexList.size());
        Assert.assertEquals(true, indexList.get(0).getGlobal());
        Assert.assertEquals(false, indexList.get(1).getGlobal());
    }

    @Test
    public void listTableIndex_TestIndexAvailable_Success() {
        List<DBTableIndex> indexList = accessor.listTableIndexes(getOBMySQLDataBaseName(), "test_index_type");
        Assert.assertTrue(indexList.get(0).getAvailable());
    }

    @Test
    public void listTableIndex_Success() {
        Map<String, List<DBTableIndex>> indexes = accessor.listTableIndexes(getOBMySQLDataBaseName());
        Assert.assertTrue(indexes.size() > 0);
    }

    @Test
    public void listTableConstraint_TestForeignKey_Success() {
        List<DBTableConstraint> constraintListList =
                accessor.listTableConstraints(getOBMySQLDataBaseName(), "test_fk_child");
        Assert.assertEquals(1, constraintListList.size());
        Assert.assertEquals(DBConstraintType.FOREIGN_KEY, constraintListList.get(0).getType());
        Assert.assertEquals("test_fk_parent", constraintListList.get(0).getReferenceTableName());
    }

    @Test
    public void listTableConstraint_TestPrimaryKey_Success() {
        List<DBTableConstraint> constraintListList =
                accessor.listTableConstraints(getOBMySQLDataBaseName(), "test_fk_parent");
        Assert.assertEquals(1, constraintListList.size());
        Assert.assertEquals(DBConstraintType.PRIMARY_KEY, constraintListList.get(0).getType());
        Assert.assertEquals(2, constraintListList.get(0).getColumnNames().size());
    }

    @Test
    public void testParseType_MultiValues_Success() {
        String dataType = "enum('x-small','small','medium','large','x-large')";
        List<String> enums = DBSchemaAccessorUtil.parseEnumValues(dataType);
        Assert.assertEquals("x-small", enums.get(0));
        Assert.assertEquals("small", enums.get(1));
        Assert.assertEquals("medium", enums.get(2));
        Assert.assertEquals("large", enums.get(3));
        Assert.assertEquals("x-large", enums.get(4));
    }

    @Test
    public void testParseType_ContainsEmptyString_Success() {
        String dataType = "enum('x-small','')";
        List<String> enums = DBSchemaAccessorUtil.parseEnumValues(dataType);
        Assert.assertEquals("x-small", enums.get(0));
        Assert.assertEquals("", enums.get(1));
    }

    @Test
    public void listSystemViews_information_schema_not_empty() {
        List<String> viewNames = accessor.showSystemViews("information_schema");
        Assert.assertTrue(!viewNames.isEmpty());
    }

    @Test
    public void listAllSystemViews_Success() {
        List<DBObjectIdentity> sysViews = accessor.listAllSystemViews(null);
        Assert.assertTrue(sysViews != null && sysViews.size() > 0);
    }

    @Test
    public void listSystemViewsWhenViewNameLikeNotNull_Success() {
        List<DBObjectIdentity> sysViews = accessor.listAllSystemViews("TABLE");
        if (CollectionUtils.isNotEmpty(sysViews)) {
            Assert.assertTrue(sysViews.stream().allMatch(o -> o.getName().toUpperCase().contains("TABLE")));
        }
    }

    @Test
    public void listSystemViews_databaseNotFound_empty() {
        List<String> viewNames = accessor.showSystemViews("databaseNotExists");
        Assert.assertTrue(viewNames.isEmpty());
    }

    @Test
    public void getPartition_Hash_Success() {
        DBTablePartition partition =
                accessor.getPartition(getOBMySQLDataBaseName(), "part_hash");
        Assert.assertEquals(5L, partition.getPartitionOption().getPartitionsNum().longValue());
        Assert.assertEquals(DBTablePartitionType.HASH, partition.getPartitionOption().getType());
    }

    @Test
    public void getTableOptions_Success() {
        DBTableOptions options =
                accessor.getTableOptions(getOBMySQLDataBaseName(), "part_hash");
        Assert.assertEquals("utf8mb4", options.getCharsetName());
    }

    @Test
    public void listTableOptions_Success() {
        Map<String, DBTableOptions> table2Options =
                accessor.listTableOptions(getOBMySQLDataBaseName());
        Assert.assertTrue(table2Options.containsKey("part_hash"));
    }

    @Test
    public void listTables_Success() {
        List<DBObjectIdentity> tables = accessor.listTables(getOBMySQLDataBaseName(), null);
        Assert.assertTrue(tables != null && tables.size() > 0);
    }

    @Test
    public void listTables_InputIsNonEmptyString_Success() {
        List<DBObjectIdentity> tables = accessor.listTables(getOBMySQLDataBaseName(), "_");
        Assert.assertTrue(tables.stream().allMatch(o -> o.getName().contains("_")));
    }

    @Test
    public void listTables_inOceanbaseSchema_Success() {
        List<DBObjectIdentity> tables = accessor.listTables("oceanbase", "job");
        Assert.assertTrue(tables != null && tables.size() > 0);
    }

    @Test
    public void listTables_inMySQLSchema_Success() {
        List<DBObjectIdentity> tables = accessor.listTables("mysql", "time");
        Assert.assertTrue(tables != null && tables.size() > 0);
    }

    @Test
    public void showViews_Success() {
        List<DBObjectIdentity> views = accessor.listViews(getOBMySQLDataBaseName());
        Assert.assertTrue(views != null && views.size() == 2);
    }

    @Test
    public void getView_Success() {
        DBView view = accessor.getView(getOBMySQLDataBaseName(), "view_test1");
        Assert.assertTrue(view != null && view.getColumns().size() == 2);
    }

    @Test
    public void showVariables_Success() {
        List<DBVariable> variables = accessor.showVariables();
        List<DBVariable> sessionVariables = accessor.showSessionVariables();
        List<DBVariable> globalVariables = accessor.showGlobalVariables();
        Assert.assertTrue(variables != null && variables.size() > 0);
        Assert.assertTrue(sessionVariables != null && sessionVariables.size() > 0);
        Assert.assertTrue(globalVariables != null && globalVariables.size() > 0);
    }

    @Test
    public void showCharset_Success() {
        List<String> charset = accessor.showCharset();
        Assert.assertTrue(charset != null && charset.size() > 0);
    }

    @Test
    public void showCollation_Success() {
        List<String> collation = accessor.showCollation();
        Assert.assertTrue(collation != null && collation.size() > 0);
    }

    @Test
    public void listFunctions_Success() {
        List<DBPLObjectIdentity> functions = accessor.listFunctions(getOBMySQLDataBaseName());
        Assert.assertTrue(functions != null && functions.size() == 1);
    }

    @Test
    public void getFunction_Success() {
        DBFunction function = accessor.getFunction(getOBMySQLDataBaseName(), "function_test");
        Assert.assertTrue(function != null
                && function.getParams().size() == 2
                && function.getReturnType() != null);
    }

    @Test
    public void listProcedures_Success() {
        List<DBPLObjectIdentity> procedures = accessor.listProcedures(getOBMySQLDataBaseName());
        Assert.assertTrue(!procedures.isEmpty());
    }

    @Test
    public void getProcedure_Success() {
        DBProcedure procedure = accessor.getProcedure(getOBMySQLDataBaseName(), "procedure_detail_test");
        Assert.assertTrue(procedure != null
                && procedure.getParams().size() == 2
                && procedure.getVariables().size() == 1);
    }

    @Test
    public void getProcedure_without_parameters_Success() {
        DBProcedure procedure = accessor.getProcedure(getOBMySQLDataBaseName(), "procedure_without_parameters");
        Assert.assertTrue(procedure != null
                && procedure.getParams().size() == 0
                && procedure.getVariables().size() == 0);
    }

    @Test
    public void getDatabase_Success() {
        DBDatabase database = accessor.getDatabase(getOBMySQLDataBaseName());
        Assert.assertNotNull(database);
    }

    @Test
    public void listDatabases_Success() {
        List<DBDatabase> databases = accessor.listDatabases();
        Assert.assertTrue(databases.size() > 0);
    }

    @Test
    public void getPartition_List_Success() {
        DBTablePartition partition =
                accessor.getPartition(getOBMySQLDataBaseName(), "part_list");
        Assert.assertEquals(5L, partition.getPartitionOption().getPartitionsNum().longValue());
        Assert.assertEquals(DBTablePartitionType.LIST, partition.getPartitionOption().getType());
        Assert.assertEquals(2, partition.getPartitionDefinitions().get(0).getValuesList().size());
    }

    @Test
    public void getPartition_Range_Success() {
        DBTablePartition partition =
                accessor.getPartition(getOBMySQLDataBaseName(), "part_range");
        Assert.assertEquals(3L, partition.getPartitionOption().getPartitionsNum().longValue());
        Assert.assertEquals(DBTablePartitionType.RANGE, partition.getPartitionOption().getType());
        Assert.assertEquals("10", partition.getPartitionDefinitions().get(0).getMaxValues().get(0));
    }

    @Test
    public void listTablePartitions_noCandidates_listSucceed() {
        Map<String, DBTablePartition> actual = accessor.listTablePartitions(getOBMySQLDataBaseName(), null);
        DBTablePartition partiHash = actual.get("part_hash");
        Assert.assertEquals(5L, partiHash.getPartitionOption().getPartitionsNum().longValue());
        Assert.assertEquals(DBTablePartitionType.HASH, partiHash.getPartitionOption().getType());

        DBTablePartition partiList = actual.get("part_list");
        Assert.assertEquals(5L, partiList.getPartitionOption().getPartitionsNum().longValue());
        Assert.assertEquals(DBTablePartitionType.LIST, partiList.getPartitionOption().getType());
        Assert.assertEquals(2, partiList.getPartitionDefinitions().get(0).getValuesList().size());

        DBTablePartition partiRange = actual.get("part_range");
        Assert.assertEquals(3L, partiRange.getPartitionOption().getPartitionsNum().longValue());
        Assert.assertEquals(DBTablePartitionType.RANGE, partiRange.getPartitionOption().getType());
        Assert.assertEquals("10", partiRange.getPartitionDefinitions().get(0).getMaxValues().get(0));
    }

    @Test
    public void listTablePartitions_candidatesExists_listSucceed() {
        Map<String, DBTablePartition> actual = accessor.listTablePartitions(getOBMySQLDataBaseName(),
                Arrays.asList("part_hash", "part_list"));
        DBTablePartition partiHash = actual.get("part_hash");
        Assert.assertEquals(5L, partiHash.getPartitionOption().getPartitionsNum().longValue());
        Assert.assertEquals(DBTablePartitionType.HASH, partiHash.getPartitionOption().getType());

        DBTablePartition partiList = actual.get("part_list");
        Assert.assertEquals(5L, partiList.getPartitionOption().getPartitionsNum().longValue());
        Assert.assertEquals(DBTablePartitionType.LIST, partiList.getPartitionOption().getType());
        Assert.assertEquals(2, partiList.getPartitionDefinitions().get(0).getValuesList().size());

        Assert.assertNull(actual.get("part_range"));
    }

    @Test
    public void listTableColumns_test_in_mysql_schema_view_as_base_table_Success() {
        List<DBTableColumn> columns = accessor.listTableColumns("mysql", "time_zone_transition");
        Assert.assertEquals(3, columns.size());
    }

    @Test
    public void listTableColumns_filterByTableName_Success() {
        Map<String, List<DBTableColumn>> table2Columns = accessor.listTableColumns(getOBMySQLDataBaseName(),
                Arrays.asList("part_hash", "part_list"));
        Assert.assertEquals(2, table2Columns.size());
    }

    @Test
    public void getTables_success() {
        Map<String, DBTable> tables = accessor.getTables(getOBMySQLDataBaseName(), null);
        Assert.assertTrue(tables.size() > 0);
    }

    private static void initVerifyColumnAttributes() {
        columnAttributes.addAll(Arrays.asList(
                ColumnAttributes.of("col1", false, false, true, null, "col1_comments"),
                ColumnAttributes.of("col2", true, false, false, null, "")));
    }

    private static void initVerifyDataTypes() {
        verifyDataTypes.addAll(Arrays.asList(
                DataType.of("col1", "int", 11, 11L, 0, null),
                DataType.of("col2", "decimal", 10, 10L, 2, 2),
                DataType.of("col3", "decimal", 10, 10L, 2, 2),
                DataType.of("col4", "bit", 8, 8L, 0, null),
                DataType.of("col5", "tinyint", 4, 4L, 0, null),
                DataType.of("col6", "smallint", 6, 6L, 0, null),
                DataType.of("col7", "mediumint", 9, 9L, 0, null),
                DataType.of("col8", "bigint", 20, 20L, 0, null),
                DataType.of("col9", "float", 10, 10L, 2, 2),
                DataType.of("col10", "double", 10, 10L, 2, 2),
                DataType.of("col11", "varchar", 10, 10L, 0, null),
                DataType.of("col12", "char", 10, 10L, 0, null),
                DataType.of("col13", "text", 65535, null, 0, null),
                DataType.of("col14", "tinytext", 255, null, 0, null),
                DataType.of("col15", "mediumtext", 16777215, null, 0, null),
                DataType.of("col16", "longtext", 50331647, null, 0, null),
                DataType.of("col17", "blob", 65535, null, 0, null),
                DataType.of("col18", "tinyblob", 255, null, 0, null),
                DataType.of("col19", "longblob", 50331647, null, 0, null),
                DataType.of("col20", "mediumblob", 16777215, null, 0, null),
                DataType.of("col21", "binary", 16, 16L, 0, null),
                DataType.of("col22", "varbinary", 16, 16L, 0, null),
                DataType.of("col23", "timestamp", 0, 0L, 0, null),
                DataType.of("col24", "time", 0, 0L, 0, null),
                DataType.of("col25", "date", 0, null, 0, null),
                DataType.of("col26", "datetime", 0, 0L, 0, null),
                DataType.of("col27", "year", 0, 0L, 0, null)));
    }

    @Data
    public static class DataType {
        private String columnName;
        private String typeName;
        private Long displayPrecision;
        private Long precision;
        private Integer displayScale;
        private Integer scale;

        public static DataType of(String columnName, String typeName, long precision, Long displayPrecision, int scale,
                Integer displayScale) {
            DataType dataType = new DataType();
            dataType.setColumnName(columnName);
            dataType.setTypeName(typeName);
            dataType.setPrecision(precision);
            dataType.setScale(scale);
            dataType.setDisplayPrecision(displayPrecision);
            dataType.setDisplayScale(displayScale);
            return dataType;
        }
    }

    @Data
    private static class ColumnAttributes {
        private String columnName;
        private Boolean nullable;
        private Boolean virtual;
        private Boolean autoIncrement;
        private String defaultValue;
        private String comments;

        static ColumnAttributes of(String columnName, boolean nullable, boolean virtual, boolean autoIncrement,
                String defaultValue,
                String comments) {
            ColumnAttributes columnAttributes = new ColumnAttributes();
            columnAttributes.setColumnName(columnName);
            columnAttributes.setNullable(nullable);
            columnAttributes.setVirtual(virtual);
            columnAttributes.setAutoIncrement(autoIncrement);
            columnAttributes.setDefaultValue(defaultValue);
            columnAttributes.setComments(comments);
            return columnAttributes;
        }
    }
}
