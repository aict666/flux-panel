package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
public class AgentClientUpdateDto {

    @NotNull(message = "服务账号 ID 不能为空")
    private Long id;

    @NotBlank(message = "服务账号名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotEmpty(message = "scope 不能为空")
    private Set<String> scopes;
}
