package com.admin.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TunnelTopologyConfigDto {

    private List<TunnelTopologyItemDto> inNodeId = new ArrayList<>();

    private List<List<TunnelTopologyItemDto>> chainNodes = new ArrayList<>();

    private List<TunnelTopologyItemDto> outNodeId = new ArrayList<>();
}
