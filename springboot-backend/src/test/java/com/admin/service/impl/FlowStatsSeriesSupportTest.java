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
}
