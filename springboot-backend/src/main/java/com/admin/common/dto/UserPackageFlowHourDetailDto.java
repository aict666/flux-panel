package com.admin.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserPackageFlowHourDetailDto {

    private HourDto hour;

    private UserPackageFlowStatsDto.SummaryDto summary;

    private UserPackageFlowStatsDto.MetaDto meta;

    private List<UserPackageFlowStatsDto.ForwardFlowStatsDto> rows = new ArrayList<>();

    @Data
    public static class HourDto {
        private Long hourTime;
        private String time;
    }
}
