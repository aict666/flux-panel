package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("agent_api_key")
public class AgentApiKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long clientId;

    private String keyPrefix;

    private String keyHash;

    private Long expiresTime;

    private Long lastUsedTime;

    private String lastUsedIp;

    private Long createdTime;

    private Long updatedTime;

    private Integer status;
}
