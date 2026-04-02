package com.admin.common.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class TunnelDto {
    
    @NotBlank(message = "隧道名称不能为空")
    private String name;

    @NotNull(message = "入口节点不能为空")
    @Valid
    private List<TunnelTopologyItemDto> inNodeId = new ArrayList<>();

    @Valid
    private List<List<TunnelTopologyItemDto>> chainNodes = new ArrayList<>();

    @Valid
    private List<TunnelTopologyItemDto> outNodeId = new ArrayList<>();

    private String inIp;

    @NotNull(message = "隧道类型不能为空")
    private Integer type;
    
    @NotNull(message = "流量计算类型不能为空")
    private Integer flow;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "流量倍率必须大于0.0")
    @DecimalMax(value = "100.0", message = "流量倍率不能大于100.0")
    private BigDecimal trafficRatio;
}
