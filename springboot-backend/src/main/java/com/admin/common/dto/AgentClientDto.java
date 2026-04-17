package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
public class AgentClientDto {

    @NotBlank(message = "服务账号名称不能为空")
    private String name;

    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    private String description;

    @NotEmpty(message = "scope 不能为空")
    private Set<String> scopes;

    @NotNull(message = "过期时间不能为空")
    private Long expiresTime;
}
