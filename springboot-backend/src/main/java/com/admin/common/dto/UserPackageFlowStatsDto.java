package com.admin.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserPackageFlowStatsDto {

    private FiltersDto filters;

    private OverviewCardsDto overviewCards;

    private RankingsDto rankings;

    private RangeDto range;

    private SummaryDto summary;

    private MetaDto meta;

    private Long defaultHourTime;

    private List<SeriesPointDto> series = new ArrayList<>();

    private List<ForwardFlowStatsDto> forwardStats = new ArrayList<>();

    private List<SeriesPointDto> trendSeries = new ArrayList<>();

    private List<TopRuleSeriesDto> topRuleSeries = new ArrayList<>();

    @Data
    public static class FiltersDto {
        private Long startTime;
        private Long endTime;
        private String granularity;
        private String metric;
    }

    @Data
    public static class OverviewCardsDto {
        private Long userCount;
        private Long tunnelCount;
        private Long forwardCount;
        private Long totalInFlow;
        private Long totalOutFlow;
        private Long totalFlow;
        private Long flowLimit;
        private Long usedFlow;
        private Integer forwardLimit;
        private Integer usedForwardCount;
        private Long peakBucketTime;
        private Long peakBucketValue;
    }

    @Data
    public static class RankingsDto {
        private String leftTitle;
        private String rightTitle;
        private List<RankingItemDto> left = new ArrayList<>();
        private List<RankingItemDto> right = new ArrayList<>();
    }

    @Data
    public static class RangeDto {
        private Long startTime;
        private Long endTime;
    }

    @Data
    public static class SummaryDto {
        private Long totalInFlow;
        private Long totalOutFlow;
        private Long totalFlow;
    }

    @Data
    public static class MetaDto {
        private String scope;
        private String rankingMode;
        private Integer totalRuleCount;
        private Integer returnedRuleCount;
        private Boolean hasSamplingGap;
        private String granularity;
        private String metric;
        private Integer topRuleCount;
        private Long peakBucketTime;
    }

    @Data
    public static class SeriesPointDto {
        private Long bucketTime;
        private Long hourTime;
        private String time;
        private Boolean sampled;
        private Long inFlow;
        private Long outFlow;
        private Long flow;
    }

    @Data
    public static class RankingItemDto {
        private Long id;
        private String name;
        private String secondaryName;
        private Long inFlow;
        private Long outFlow;
        private Long flow;
    }

    @Data
    public static class ForwardFlowStatsDto {
        private Long id;
        private String name;
        private String userName;
        private Integer tunnelId;
        private String tunnelName;
        private String inAddress;
        private String remoteAddr;
        private Long inFlow;
        private Long outFlow;
        private Long flow;
    }

    @Data
    public static class TopRuleSeriesDto {
        private Long id;
        private String name;
        private String userName;
        private Long totalInFlow;
        private Long totalOutFlow;
        private Long totalFlow;
        private List<SeriesPointDto> series = new ArrayList<>();
    }
}
