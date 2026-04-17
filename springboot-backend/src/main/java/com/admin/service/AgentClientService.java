package com.admin.service;

import com.admin.common.dto.AgentClientDto;
import com.admin.common.dto.AgentClientUpdateDto;
import com.admin.common.dto.AgentKeyRotateDto;
import com.admin.common.lang.R;
import com.admin.entity.AgentClient;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentClientService extends IService<AgentClient> {

    R createClient(AgentClientDto dto);

    R updateClient(AgentClientUpdateDto dto);

    R deleteClient(Long clientId);

    R rotateKey(Long clientId, AgentKeyRotateDto dto);
}
