package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AgentDescriptorDto {

    @NotBlank(message = "descriptor 格式不能为空")
    private String format;
}
