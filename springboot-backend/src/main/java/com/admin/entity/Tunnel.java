package com.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * <p>
 * 隧道实体类
 * </p>
 *
 * @author QAQ
 * @since 2025-06-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(autoResultMap = true)
public class Tunnel extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;

    private Integer type;

    private String protocol;

    private int flow;

    private BigDecimal trafficRatio;

    private String inIp;

    @TableField("topology_json")
    private String topologyJson;
}
