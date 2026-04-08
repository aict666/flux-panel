package com.admin.service.impl;

import com.admin.common.dto.UserPackageFlowStatsDto;
import com.admin.entity.StatisticsFlow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowStatsSeriesSupportTest {

    private static final long HOUR_MILLIS = 60L * 60L * 1000L;

    @Test
    void shouldMarkMissingHoursAsUnsampledGapsInsteadOfZeroValues() {
        long hourOne = 1_000L;
        long hourTwo = hourOne + HOUR_MILLIS;
        long hourThree = hourTwo + HOUR_MILLIS;

        StatisticsFlow sampledBucket = new StatisticsFlow();
        sampledBucket.setHourTime(hourOne);
        sampledBucket.setInFlow(7L);
        sampledBucket.setOutFlow(3L);
        sampledBucket.setFlow(10L);

        FlowStatsSeriesSupport.SeriesBuildResult result = FlowStatsSeriesSupport.buildHistoricalSeries(
                hourOne,
                hourThree,
                List.of(sampledBucket),
                hourTime -> "hour-" + hourTime
        );

        assertTrue(result.hasSamplingGap());
        assertEquals(3, result.getSeries().size());
        assertTrue(result.getSeries().get(0).getSampled());
        assertEquals(10L, result.getSeries().get(0).getFlow());

        UserPackageFlowStatsDto.SeriesPointDto gapPoint = result.getSeries().get(1);
        assertFalse(gapPoint.getSampled());
        assertNull(gapPoint.getInFlow());
        assertNull(gapPoint.getOutFlow());
        assertNull(gapPoint.getFlow());
        assertEquals("hour-" + hourTwo, gapPoint.getTime());
    }

    @Test
    void shouldKeepZeroFlowHoursAsSampledData() {
        long hourOne = 1_000L;

        StatisticsFlow zeroBucket = new StatisticsFlow();
        zeroBucket.setHourTime(hourOne);
        zeroBucket.setInFlow(0L);
        zeroBucket.setOutFlow(0L);
        zeroBucket.setFlow(0L);

        FlowStatsSeriesSupport.SeriesBuildResult result = FlowStatsSeriesSupport.buildHistoricalSeries(
                hourOne,
                hourOne,
                List.of(zeroBucket),
                hourTime -> "hour-" + hourTime
        );

        assertFalse(result.hasSamplingGap());
        assertEquals(1, result.getSeries().size());
        assertTrue(result.getSeries().get(0).getSampled());
        assertEquals(0L, result.getSeries().get(0).getInFlow());
        assertEquals(0L, result.getSeries().get(0).getOutFlow());
        assertEquals(0L, result.getSeries().get(0).getFlow());
    }

    @Test
    void shouldExcludeUnsampledGapsFromSummaryTotals() {
        UserPackageFlowStatsDto.SeriesPointDto sampledPoint = new UserPackageFlowStatsDto.SeriesPointDto();
        sampledPoint.setSampled(true);
        sampledPoint.setInFlow(8L);
        sampledPoint.setOutFlow(2L);
        sampledPoint.setFlow(10L);

        UserPackageFlowStatsDto.SeriesPointDto gapPoint = new UserPackageFlowStatsDto.SeriesPointDto();
        gapPoint.setSampled(false);
        gapPoint.setInFlow(null);
        gapPoint.setOutFlow(null);
        gapPoint.setFlow(null);

        UserPackageFlowStatsDto.SummaryDto summary = FlowStatsSeriesSupport.buildSummary(List.of(sampledPoint, gapPoint));

        assertEquals(8L, summary.getTotalInFlow());
        assertEquals(2L, summary.getTotalOutFlow());
        assertEquals(10L, summary.getTotalFlow());
    }

    @Test
    void shouldAggregateHourlySeriesIntoDailyBucketsWhileKeepingPartialSampling() {
        long dayOneHourOne = 1_000L;
        long dayOneHourTwo = dayOneHourOne + HOUR_MILLIS;
        long dayTwoHourOne = dayOneHourOne + 24 * HOUR_MILLIS;
        long dayTwoHourTwo = dayTwoHourOne + HOUR_MILLIS;

        UserPackageFlowStatsDto.SeriesPointDto sampledDayOnePoint = new UserPackageFlowStatsDto.SeriesPointDto();
        sampledDayOnePoint.setHourTime(dayOneHourOne);
        sampledDayOnePoint.setTime("hour-1");
        sampledDayOnePoint.setSampled(true);
        sampledDayOnePoint.setInFlow(8L);
        sampledDayOnePoint.setOutFlow(2L);
        sampledDayOnePoint.setFlow(10L);

        UserPackageFlowStatsDto.SeriesPointDto gapDayOnePoint = new UserPackageFlowStatsDto.SeriesPointDto();
        gapDayOnePoint.setHourTime(dayOneHourTwo);
        gapDayOnePoint.setTime("hour-2");
        gapDayOnePoint.setSampled(false);

        UserPackageFlowStatsDto.SeriesPointDto gapDayTwoPoint = new UserPackageFlowStatsDto.SeriesPointDto();
        gapDayTwoPoint.setHourTime(dayTwoHourOne);
        gapDayTwoPoint.setTime("hour-3");
        gapDayTwoPoint.setSampled(false);

        UserPackageFlowStatsDto.SeriesPointDto sampledDayTwoPoint = new UserPackageFlowStatsDto.SeriesPointDto();
        sampledDayTwoPoint.setHourTime(dayTwoHourTwo);
        sampledDayTwoPoint.setTime("hour-4");
        sampledDayTwoPoint.setSampled(true);
        sampledDayTwoPoint.setInFlow(3L);
        sampledDayTwoPoint.setOutFlow(1L);
        sampledDayTwoPoint.setFlow(4L);

        FlowStatsSeriesSupport.SeriesBuildResult result = FlowStatsSeriesSupport.aggregateSeriesByDay(
                List.of(sampledDayOnePoint, gapDayOnePoint, gapDayTwoPoint, sampledDayTwoPoint),
                bucketTime -> "day-" + bucketTime
        );

        assertTrue(result.hasSamplingGap());
        assertEquals(2, result.getSeries().size());
        assertTrue(result.getSeries().get(0).getSampled());
        assertEquals(10L, result.getSeries().get(0).getFlow());
        assertTrue(result.getSeries().get(1).getSampled());
        assertEquals(4L, result.getSeries().get(1).getFlow());
    }

    @Test
    void shouldKeepWholeDayAsGapWhenEveryHourInThatDayIsUnsampled() {
        long dayOneHourOne = 1_000L;
        long dayOneHourTwo = dayOneHourOne + HOUR_MILLIS;

        UserPackageFlowStatsDto.SeriesPointDto gapOne = new UserPackageFlowStatsDto.SeriesPointDto();
        gapOne.setHourTime(dayOneHourOne);
        gapOne.setTime("hour-1");
        gapOne.setSampled(false);

        UserPackageFlowStatsDto.SeriesPointDto gapTwo = new UserPackageFlowStatsDto.SeriesPointDto();
        gapTwo.setHourTime(dayOneHourTwo);
        gapTwo.setTime("hour-2");
        gapTwo.setSampled(false);

        FlowStatsSeriesSupport.SeriesBuildResult result = FlowStatsSeriesSupport.aggregateSeriesByDay(
                List.of(gapOne, gapTwo),
                bucketTime -> "day-" + bucketTime
        );

        assertTrue(result.hasSamplingGap());
        assertEquals(1, result.getSeries().size());
        assertFalse(result.getSeries().get(0).getSampled());
        assertNull(result.getSeries().get(0).getInFlow());
        assertNull(result.getSeries().get(0).getOutFlow());
        assertNull(result.getSeries().get(0).getFlow());
    }
}
