package com.admin.common.dto;

import lombok.Data;

@Data
public class AgentAuditQueryDto {

    private Long clientId;

    private Boolean success;

    private Long startTime;

    private Long endTime;
}
