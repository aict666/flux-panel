package com.admin.common.task;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StatisticsFlowAsyncUnitTest {

    @Test
    void shouldCaptureCurrentHourBucketsOnStartup() {
        StatisticsFlowAsync task = Mockito.spy(new StatisticsFlowAsync());
        doNothing().when(task).captureCurrentHourBuckets();

        task.captureCurrentHourBucketsOnStartup();

        verify(task, times(1)).captureCurrentHourBuckets();
    }

    @Test
    void shouldCaptureCurrentHourBucketsOnFiveMinuteSchedule() {
        StatisticsFlowAsync task = Mockito.spy(new StatisticsFlowAsync());
        doNothing().when(task).captureCurrentHourBuckets();

        task.statistics_flow();

        verify(task, times(1)).captureCurrentHourBuckets();
    }
}
