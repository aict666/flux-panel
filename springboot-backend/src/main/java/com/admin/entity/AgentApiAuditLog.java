package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("agent_api_audit_log")
public class AgentApiAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long clientId;

    private String clientName;

    private String requestPath;

    private String httpMethod;

    private String action;

    private Integer statusCode;

    private Boolean success;

    private Long durationMs;

    private String requestIp;

    private String errorMessage;

    private Long createdTime;

    private Long updatedTime;

    private Integer status;
}
