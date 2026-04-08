package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class FlowStatsQueryDto {

    @NotNull(message = "开始时间不能为空")
    private Long startTime;

    @NotNull(message = "结束时间不能为空")
    private Long endTime;

    @NotBlank(message = "粒度不能为空")
    private String granularity;

    @NotBlank(message = "统计指标不能为空")
    private String metric;
}
