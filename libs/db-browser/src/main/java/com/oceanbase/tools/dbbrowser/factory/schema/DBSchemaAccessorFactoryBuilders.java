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

package com.oceanbase.tools.dbbrowser.factory.schema;

import com.oceanbase.tools.dbbrowser.factory.DBBrowserFactoryBuilders;

public class DBSchemaAccessorFactoryBuilders implements DBBrowserFactoryBuilders {

    @Override
    public <Builder> Builder getForMySQL() {
        return null;
    }

    @Override
    public <Builder> Builder getForOracle() {
        return null;
    }

    @Override
    public OBMySQLDBSchemaAccessorFactoryBuilder getForOBMySQL() {
        return new OBMySQLDBSchemaAccessorFactoryBuilder();
    }

    @Override
    public OBOracleDBSchemaAccessorFactoryBuilder getForOBOracle() {
        return new OBOracleDBSchemaAccessorFactoryBuilder();
    }

    @Override
    public <Builder> Builder getForDoris() {
        return null;
    }

}
