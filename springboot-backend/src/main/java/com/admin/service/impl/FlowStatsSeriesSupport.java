package com.admin.service.impl;

import com.admin.common.dto.UserPackageFlowStatsDto;
import com.admin.entity.StatisticsFlow;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

final class FlowStatsSeriesSupport {

    private static final long HOUR_MILLIS = 60L * 60L * 1000L;

    private FlowStatsSeriesSupport() {
    }

    static SeriesBuildResult buildHistoricalSeries(long startTime,
                                                   long endTime,
                                                   List<StatisticsFlow> flowList,
                                                   LongFunction<String> timeFormatter) {
        if (endTime < startTime) {
            return new SeriesBuildResult(new ArrayList<>(), false);
        }

        Map<Long, UserPackageFlowStatsDto.SeriesPointDto> flowMap = new LinkedHashMap<>();
        for (StatisticsFlow statisticsFlow : flowList) {
            if (statisticsFlow.getHourTime() == null) {
                continue;
            }

            UserPackageFlowStatsDto.SeriesPointDto point = flowMap.computeIfAbsent(
                    statisticsFlow.getHourTime(),
                    hourTime -> createSampledPoint(hourTime, timeFormatter.apply(hourTime))
            );
            point.setInFlow(defaultLong(point.getInFlow()) + defaultLong(statisticsFlow.getInFlow()));
            point.setOutFlow(defaultLong(point.getOutFlow()) + defaultLong(statisticsFlow.getOutFlow()));
            point.setFlow(defaultLong(point.getFlow()) + defaultLong(statisticsFlow.getFlow()));
        }

        boolean hasSamplingGap = false;
        List<UserPackageFlowStatsDto.SeriesPointDto> result = new ArrayList<>();
        for (long cursor = startTime; cursor <= endTime; cursor += HOUR_MILLIS) {
            UserPackageFlowStatsDto.SeriesPointDto point = flowMap.get(cursor);
            if (point == null) {
                point = createGapPoint(cursor, timeFormatter.apply(cursor));
                hasSamplingGap = true;
            }
            result.add(point);
        }

        return new SeriesBuildResult(result, hasSamplingGap);
    }

    static UserPackageFlowStatsDto.SummaryDto buildSummary(List<UserPackageFlowStatsDto.SeriesPointDto> series) {
        long totalInFlow = 0L;
        long totalOutFlow = 0L;
        long totalFlow = 0L;

        for (UserPackageFlowStatsDto.SeriesPointDto point : series) {
            if (Boolean.FALSE.equals(point.getSampled())) {
                continue;
            }
            totalInFlow += defaultLong(point.getInFlow());
            totalOutFlow += defaultLong(point.getOutFlow());
            totalFlow += defaultLong(point.getFlow());
        }

        UserPackageFlowStatsDto.SummaryDto summary = new UserPackageFlowStatsDto.SummaryDto();
        summary.setTotalInFlow(totalInFlow);
        summary.setTotalOutFlow(totalOutFlow);
        summary.setTotalFlow(totalFlow);
        return summary;
    }

    static UserPackageFlowStatsDto.SeriesPointDto createSampledPoint(long hourTime, String time) {
        UserPackageFlowStatsDto.SeriesPointDto point = new UserPackageFlowStatsDto.SeriesPointDto();
        point.setHourTime(hourTime);
        point.setTime(time);
        point.setSampled(true);
        point.setInFlow(0L);
        point.setOutFlow(0L);
        point.setFlow(0L);
        return point;
    }

    private static UserPackageFlowStatsDto.SeriesPointDto createGapPoint(long hourTime, String time) {
        UserPackageFlowStatsDto.SeriesPointDto point = new UserPackageFlowStatsDto.SeriesPointDto();
        point.setHourTime(hourTime);
        point.setTime(time);
        point.setSampled(false);
        point.setInFlow(null);
        point.setOutFlow(null);
        point.setFlow(null);
        return point;
    }

    private static long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    @Getter
    static final class SeriesBuildResult {
        private final List<UserPackageFlowStatsDto.SeriesPointDto> series;
        private final boolean hasSamplingGap;

        private SeriesBuildResult(List<UserPackageFlowStatsDto.SeriesPointDto> series, boolean hasSamplingGap) {
            this.series = series;
            this.hasSamplingGap = hasSamplingGap;
        }

        boolean hasSamplingGap() {
            return hasSamplingGap;
        }
    }
}
