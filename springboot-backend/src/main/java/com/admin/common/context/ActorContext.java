package com.admin.common.context;

import java.util.Collections;
import java.util.Set;

public class ActorContext {

    private final ActorType actorType;
    private final Integer userId;
    private final String userName;
    private final Integer roleId;
    private final Long clientId;
    private final String clientName;
    private final Set<String> scopes;

    public ActorContext(
            ActorType actorType,
            Integer userId,
            String userName,
            Integer roleId,
            Long clientId,
            String clientName,
            Set<String> scopes
    ) {
        this.actorType = actorType;
        this.userId = userId;
        this.userName = userName;
        this.roleId = roleId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.scopes = scopes == null ? Collections.emptySet() : Set.copyOf(scopes);
    }

    public ActorType getActorType() {
        return actorType;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public boolean isUser() {
        return actorType == ActorType.USER;
    }

    public boolean isAgent() {
        return actorType == ActorType.AGENT;
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
