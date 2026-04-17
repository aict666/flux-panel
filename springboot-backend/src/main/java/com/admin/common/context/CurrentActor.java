package com.admin.common.context;

public class CurrentActor {

    private final ActorType actorType;
    private final Integer userId;
    private final String userName;
    private final Integer roleId;
    private final Long clientId;
    private final String clientName;

    public CurrentActor(ActorType actorType, Integer userId, String userName, Integer roleId, Long clientId, String clientName) {
        this.actorType = actorType;
        this.userId = userId;
        this.userName = userName;
        this.roleId = roleId;
        this.clientId = clientId;
        this.clientName = clientName;
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

    public boolean isAgent() {
        return actorType == ActorType.AGENT;
    }
}
