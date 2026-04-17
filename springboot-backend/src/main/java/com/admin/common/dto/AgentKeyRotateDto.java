package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AgentKeyRotateDto {

    @NotNull(message = "过期时间不能为空")
    private Long expiresTime;
}
