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
package com.oceanbase.odc.service.config;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.odc.service.config.model.ConfigurationMeta;

public class OrganizationConfigMetaServiceTest {

    OrganizationConfigMetaService organizationConfigMetaService = new OrganizationConfigMetaService();

    @Before
    public void setup() {
        organizationConfigMetaService.init();
    }

    @Test(expected = IllegalArgumentException.class)
    public void getConfigMeta_KeyNotExists_IllegalArgument() {
        organizationConfigMetaService.getConfigMeta("key.not.exists");
    }

    @Test
    public void getConfigMeta_KeyExists_NotNull() {
        ConfigurationMeta configMeta =
                organizationConfigMetaService.getConfigMeta("odc.sqlexecute.default.queryLimit");
        Assert.assertNotNull(configMeta);
    }

    @Test
    public void getConfigMeta_CustomDataSourceKeyExists_NotNull() {
        ConfigurationMeta configMeta =
                organizationConfigMetaService.getConfigMeta("odc.security.default.customDataSourceEncryptionKey");
        Assert.assertNotNull(configMeta);
        System.out.println(configMeta);
        Assert.assertEquals("odc.security.default.customDataSourceEncryptionKey", configMeta.getKey());
        Assert.assertEquals("", configMeta.getDefaultValue());
    }

    @Test
    public void getAllList_KeyExists_NotNull() {
        List<ConfigurationMeta> configMetaList = organizationConfigMetaService.listAllConfigMetas();
        System.out.println(configMetaList);
        Assert.assertEquals(6, configMetaList.size());
    }
}
