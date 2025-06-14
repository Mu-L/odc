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
package com.oceanbase.odc.agent.runtime;

import java.util.Collections;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.oceanbase.odc.service.objectstorage.cloud.CloudObjectStorageService;
import com.oceanbase.odc.service.task.Task;
import com.oceanbase.odc.service.task.caller.JobContext;
import com.oceanbase.odc.service.task.constants.JobParametersKeyConstants;
import com.oceanbase.odc.service.task.executor.TaskResult;
import com.oceanbase.odc.service.task.schedule.JobIdentity;

/**
 * @author longpeng.zlp
 * @date 2024/11/7 17:45
 */
public class TaskMonitorTest {

    @Test
    public void testTaskMonitorReportRetryFailed() {
        TaskReporter taskReporter = Mockito.mock(TaskReporter.class);
        Mockito.when(taskReporter.report(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(false);
        TaskMonitor taskMonitor = new TaskMonitor(Mockito.mock(TaskContainer.class), taskReporter, Mockito.mock(
                CloudObjectStorageService.class));
        Assert.assertFalse(taskMonitor.reportTaskResultWithRetry(new TaskResult(), 3, 1));
    }

    @Test
    public void testTaskMonitorReportRetrySuccess() {
        TaskReporter taskReporter = Mockito.mock(TaskReporter.class);
        Mockito.when(taskReporter.report(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(true);
        TaskMonitor taskMonitor = new TaskMonitor(Mockito.mock(TaskContainer.class), taskReporter, Mockito.mock(
                CloudObjectStorageService.class));
        Assert.assertTrue(taskMonitor.reportTaskResultWithRetry(new TaskResult(), 3, 1));
    }

    @Test
    public void testTaskMonitorTimeout() {
        TaskReporter taskReporter = Mockito.mock(TaskReporter.class);
        Mockito.when(taskReporter.report(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(true);
        TaskMonitor taskMonitor = new TaskMonitor(createContainer(1000), taskReporter, Mockito.mock(
                CloudObjectStorageService.class));
        Assert.assertTrue(taskMonitor.isTimeout());
    }

    @Test
    public void testTaskMonitorNotTimeout() {
        TaskReporter taskReporter = Mockito.mock(TaskReporter.class);
        Mockito.when(taskReporter.report(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(true);
        TaskMonitor taskMonitor =
                new TaskMonitor(createContainer(System.currentTimeMillis() + 100000), taskReporter, Mockito.mock(
                        CloudObjectStorageService.class));
        Assert.assertTrue(!taskMonitor.isTimeout());
    }

    protected TaskContainer createContainer(long endTimeMs) {
        TaskContainer taskContainer = Mockito.mock(TaskContainer.class);
        Task task = Mockito.mock(Task.class);
        JobContext jobContext = Mockito.mock(JobContext.class);
        JobIdentity jobIdentity = JobIdentity.of(1024L);
        Map<String, String> jobParameters = Collections
                .singletonMap(JobParametersKeyConstants.TASK_EXECUTION_END_TIME_MILLIS, String.valueOf(endTimeMs));
        Mockito.when(jobContext.getJobParameters()).thenReturn(jobParameters);
        Mockito.when(jobContext.getJobIdentity()).thenReturn(jobIdentity);
        Mockito.when(task.getJobContext()).thenReturn(jobContext);
        Mockito.when(taskContainer.getTask()).thenReturn(task);
        return taskContainer;
    }
}
