package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("forward_statistics_flow")
public class ForwardStatisticsFlow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long forwardId;

    private String forwardName;

    private Long tunnelId;

    private String tunnelName;

    private Long inFlow;

    private Long outFlow;

    private Long flow;

    private Long totalInFlow;

    private Long totalOutFlow;

    private Long totalFlow;

    private Long hourTime;

    private String time;

    private Long createdTime;
}
