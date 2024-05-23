/*
 * Copyright (c) 2024 OceanBase.
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

package com.oceanbase.tools.dbbrowser.factory.editor.table;

import com.oceanbase.tools.dbbrowser.factory.DBBrowserFactoryBuilders;
import com.oceanbase.tools.dbbrowser.factory.editor.table.OBMySQLDBTableEditorFactoryBuilder;

public class DBTableEditorFactoryBuilders implements DBBrowserFactoryBuilders {
    @Override
    public <Builder> Builder getForMySQL() {
        return null;
    }

    @Override
    public <Builder> Builder getForOracle() {
        return null;
    }

    @Override
    public OBMySQLDBTableEditorFactoryBuilder getForOBMySQL() {
        return new OBMySQLDBTableEditorFactoryBuilder();
    }

    @Override
    public OBOracleDBTableEditorFactoryBuilder getForOBOracle() {
        return new OBOracleDBTableEditorFactoryBuilder();
    }

    @Override
    public <Builder> Builder getForDoris() {
        return null;
    }
}
