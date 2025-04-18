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
package com.oceanbase.odc.service.resourcegroup.model;

import java.util.List;

import lombok.Data;

/**
 * @author wenniu.ly
 * @date 2021/7/28
 */

@Data
public class ResourceContext {
    private String field;
    private Long id;
    /**
     * only works when id is null. 'all' means all resource ids(*); 'creator' means creator's resource
     * ids
     */
    private ResourceIdExtractRule idExtractRule;
    private List<ResourceContext> subContexts;

    public enum ResourceIdExtractRule {
        ALL("*"),
        CREATOR("CREATOR");

        private final String rule;

        ResourceIdExtractRule(String rule) {
            this.rule = rule;
        }

        public String getRule() {
            return rule;
        }

        public static ResourceIdExtractRule fromString(String rule) {
            for (ResourceIdExtractRule r : ResourceIdExtractRule.values()) {
                if (r.getRule().equalsIgnoreCase(rule)) {
                    return r;
                }
            }
            throw new IllegalArgumentException("No constant with rule " + rule + " found");
        }
    }
}
