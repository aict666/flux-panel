package com.admin.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserPackageFlowStatsDto {

    private RangeDto range;

    private SummaryDto summary;

    private MetaDto meta;

    private Long defaultHourTime;

    private List<SeriesPointDto> series = new ArrayList<>();

    private List<ForwardFlowStatsDto> forwardStats = new ArrayList<>();

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
    }

    @Data
    public static class SeriesPointDto {
        private Long hourTime;
        private String time;
        private Boolean sampled;
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
}
