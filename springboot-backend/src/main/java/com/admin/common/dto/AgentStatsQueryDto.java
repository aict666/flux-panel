package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AgentStatsQueryDto {

    @NotNull(message = "开始时间不能为空")
    private Long startTime;

    @NotNull(message = "结束时间不能为空")
    private Long endTime;

    private String granularity;

    private String metric;

    private Long userId;

    private Long tunnelId;

    private Long forwardId;

    private Long hourTime;
}
