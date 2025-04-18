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
package com.oceanbase.odc.service.iam;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.oceanbase.odc.core.authority.model.DefaultSecurityResource;
import com.oceanbase.odc.core.authority.model.SecurityResource;
import com.oceanbase.odc.service.iam.auth.AuthenticationFacade;
import com.oceanbase.odc.service.resourcegroup.model.ResourceContext;
import com.oceanbase.odc.service.resourcegroup.model.ResourceContext.ResourceIdExtractRule;

/**
 * @Author: Lebie
 * @Date: 2025/2/27 15:12
 * @Description: []
 */
@Component
public class AllResourceIdExtractRuleHandler implements ResourceIdExtractRuleHandler {
    @Override
    public boolean supports(ResourceIdExtractRule rule) {
        return ResourceIdExtractRule.ALL == rule;
    }

    @Override
    public List<SecurityResource> handle(ResourceContext context, AuthenticationFacade authFacade) {
        return Collections.singletonList(new DefaultSecurityResource("*", context.getField()));
    }
}
