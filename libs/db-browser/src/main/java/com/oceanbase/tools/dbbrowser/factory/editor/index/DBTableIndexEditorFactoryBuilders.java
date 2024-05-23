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

package com.oceanbase.tools.dbbrowser.factory.editor.index;

import com.oceanbase.tools.dbbrowser.factory.DBBrowserFactoryBuilders;

public class DBTableIndexEditorFactoryBuilders implements DBBrowserFactoryBuilders {
    @Override
    public <Builder> Builder forMySQL() {
        return null;
    }

    @Override
    public <Builder> Builder forOracle() {
        return null;
    }

    @Override
    public OBMySQLDBTableIndexEditorFactoryBuilder forOBMySQL() {
        return new OBMySQLDBTableIndexEditorFactoryBuilder();
    }

    @Override
    public OBOracleDBTableIndexEditorFactoryBuilder forOBOracle() {
        return new OBOracleDBTableIndexEditorFactoryBuilder();
    }

    @Override
    public <Builder> Builder forDoris() {
        return null;
    }
}
