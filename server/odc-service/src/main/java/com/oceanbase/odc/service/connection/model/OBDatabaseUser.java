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
package com.oceanbase.odc.service.connection.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

/**
 * 公有云/多云 数据库用户
 */
@Data
public class OBDatabaseUser {
    /**
     * 数据库用户名
     */
    @JsonAlias("UserName")
    private String userName;

    /**
     * 数据库用户类型
     */
    @JsonAlias("UserType")
    private OBDatabaseUserType userType;

    @JsonAlias("Password")
    private String password;

    @JsonAlias("Databases")
    private List<OBDatabase> databases;

    @JsonAlias("UserStatus")
    private String userStatus;

}
