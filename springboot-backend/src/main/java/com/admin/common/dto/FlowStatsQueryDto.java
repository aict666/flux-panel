package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class FlowStatsQueryDto {

    @NotNull(message = "开始时间不能为空")
    private Long startTime;

    @NotNull(message = "结束时间不能为空")
    private Long endTime;
}
