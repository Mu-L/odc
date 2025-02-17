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
package com.oceanbase.odc.service.sqlcheck.rule;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.sqlcheck.SqlCheckUtil;
import com.oceanbase.tools.sqlparser.statement.Expression;
import com.oceanbase.tools.sqlparser.statement.Operator;
import com.oceanbase.tools.sqlparser.statement.expression.CompoundExpression;
import com.oceanbase.tools.sqlparser.statement.expression.NullExpression;
import com.oceanbase.tools.sqlparser.statement.expression.RelationReference;
import com.oceanbase.tools.sqlparser.statement.select.SelectBody;

/**
 * {@link OracleNoNotNullAtInExpression}
 *
 * @author yh263208
 * @date 2022-12-26 17:44
 * @since ODC_release_4.1.0
 * @see BaseNoNotNullAtInExpr
 */
public class OracleNoNotNullAtInExpression extends BaseNoNotNullAtInExpr {

    @Override
    protected boolean containsNotNullForColumnReference(SelectBody selectBody) {
        List<RelationReference> columns = selectBody.getSelectItems().stream()
                .filter(p -> p.getColumn() instanceof RelationReference)
                .map(p -> (RelationReference) p.getColumn()).collect(Collectors.toList());
        Expression where = selectBody.getWhere();
        if (where == null) {
            return false;
        }
        List<Expression> exprs = SqlCheckUtil.findAll(where, expr -> {
            if (!(expr instanceof CompoundExpression)) {
                return false;
            }
            CompoundExpression c = (CompoundExpression) expr;
            if (!(c.getLeft() instanceof RelationReference)) {
                return false;
            }
            if (c.getOperator() != Operator.NE) {
                return false;
            }
            return c.getRight() instanceof NullExpression;
        });
        columns.removeIf(r -> exprs.stream().anyMatch(expr -> {
            CompoundExpression c = (CompoundExpression) expr;
            RelationReference cr = (RelationReference) c.getLeft();
            return getColumnName(r).equalsIgnoreCase(getColumnName(cr));
        }));
        return columns.isEmpty();
    }

    @Override
    public List<DialectType> getSupportsDialectTypes() {
        return Arrays.asList(DialectType.OB_ORACLE, DialectType.ORACLE);
    }

    private String getColumnName(RelationReference reference) {
        Expression expr = reference.getReference();
        if (!(expr instanceof RelationReference)) {
            return reference.getRelationName();
        }
        while (true) {
            String relation = ((RelationReference) expr).getRelationName();
            expr = ((RelationReference) expr).getReference();
            if (!(expr instanceof RelationReference)) {
                return relation;
            }
        }
    }

}
