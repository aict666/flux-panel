package com.admin.common.dto;

import lombok.Data;

import java.util.Set;

@Data
public class AgentScopeDto {

    private Set<String> scopes;
}
