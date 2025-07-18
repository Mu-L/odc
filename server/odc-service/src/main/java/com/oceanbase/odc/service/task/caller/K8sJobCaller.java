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

package com.oceanbase.odc.service.task.caller;

import java.util.Date;
import java.util.Optional;

import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.service.resource.ResourceID;
import com.oceanbase.odc.service.resource.ResourceLocation;
import com.oceanbase.odc.service.resource.ResourceManager;
import com.oceanbase.odc.service.resource.ResourceState;
import com.oceanbase.odc.service.resource.ResourceWithID;
import com.oceanbase.odc.service.task.exception.JobException;
import com.oceanbase.odc.service.task.resource.K8sPodResource;
import com.oceanbase.odc.service.task.resource.K8sResourceContext;
import com.oceanbase.odc.service.task.resource.PodConfig;
import com.oceanbase.odc.service.task.schedule.JobIdentity;
import com.oceanbase.odc.service.task.supervisor.endpoint.ExecutorEndpoint;
import com.oceanbase.odc.service.task.util.JobUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yaobin
 * @date 2023-11-15
 * @since 4.2.4
 */
@Slf4j
public class K8sJobCaller extends BaseJobCaller {
    /**
     * base job config
     */
    private final PodConfig defaultPodConfig;
    private final ResourceManager resourceManager;
    private final String resourceType;
    private final Date jobCreateTime;

    public K8sJobCaller(PodConfig podConfig, ResourceManager resourceManager, String resourceType, Date jobCreateTime) {
        this.defaultPodConfig = podConfig;
        this.resourceManager = resourceManager;
        this.resourceType = resourceType;
        this.jobCreateTime = jobCreateTime;
    }

    @Override
    public ExecutorInfo doStart(JobContext context) throws JobException {
        try {
            ResourceLocation resourceLocation = buildResourceLocation(context);
            ResourceWithID<K8sPodResource> resource =
                    resourceManager.create(resourceLocation, buildK8sResourceContext(context, resourceLocation));
            K8sPodResource k8sPodResource = resource.getResource();
            String arn = k8sPodResource.resourceID().getIdentifier();
            ExecutorIdentifier executorIdentifier =
                    DefaultExecutorIdentifier.builder().namespace(resource.getResource().getNamespace())
                            .host(resource.getResource().getPodIpAddress())
                            .port(Integer.valueOf(resource.getResource().getServicePort()))
                            .executorName(arn).build();
            ExecutorEndpoint executorEndpoint = new ExecutorEndpoint("k8s", resource.getResource().getPodIpAddress(),
                    -1, -1,
                    Integer.valueOf(resource.getResource().getServicePort()), executorIdentifier.toString());
            return new ExecutorInfo(k8sPodResource.resourceID(),
                    executorEndpoint);
        } catch (Throwable e) {
            throw new JobException("doStart failed for " + context, e);
        }
    }

    protected K8sResourceContext buildK8sResourceContext(JobContext context, ResourceLocation resourceLocation) {
        String jobName = JobUtils.generateExecutorName(context.getJobIdentity(), jobCreateTime);
        return new K8sResourceContext(defaultPodConfig, jobName, resourceLocation.getRegion(),
                resourceLocation.getGroup(),
                resourceType, context);
    }

    protected ResourceLocation buildResourceLocation(JobContext context) {
        String defaultRegion = StringUtils.isEmpty(defaultPodConfig.getRegion()) ? ResourceIDUtil.DEFAULT_PROP_VALUE
                : defaultPodConfig.getRegion();
        // TODO(tianke): confirm is this correct?
        String region = ResourceIDUtil.checkAndGetJobProperties(context.getJobProperties(),
                ResourceIDUtil.REGION_PROP_NAME, defaultRegion);
        // TODO(tianke): cloud provider info should be given before job start
        String group = ResourceIDUtil.checkAndGetJobProperties(context.getJobProperties(),
                ResourceIDUtil.GROUP_PROP_NAME, ResourceIDUtil.DEFAULT_PROP_VALUE);
        return new ResourceLocation(region, group);
    }

    @Override
    protected void doFinish(JobIdentity ji, ExecutorIdentifier ei, ResourceID resourceID)
            throws JobException {
        resourceManager.release(resourceID);
        updateExecutorDestroyed(ji);
    }

    @Override
    protected boolean canBeFinish(JobIdentity ji, ExecutorIdentifier ei, ResourceID resourceID) {
        return resourceManager.canBeDestroyed(resourceID);
    }

    @Override
    protected boolean isExecutorExist(ExecutorIdentifier identifier, ResourceID resourceID)
            throws JobException {
        try {
            Optional<K8sPodResource> executorOptional =
                    resourceManager.query(resourceID);
            return executorOptional.isPresent() && !ResourceState.isDestroying(
                    executorOptional.get().getResourceState());
        } catch (Throwable e) {
            throw new JobException("invoke isExecutor failed", e);
        }
    }
}
