package com.admin.common.dto;

import lombok.Data;

@Data
public class TunnelTopologyItemDto {

    /**
     * node / tunnel
     */
    private String itemType;

    private Long nodeId;

    private Long refTunnelId;

    private String refTunnelName;

    private String protocol;

    private String strategy;

    private Integer hopIndex;
}
