package com.admin.service.impl;

import com.admin.entity.AgentApiKey;
import com.admin.mapper.AgentApiKeyMapper;
import com.admin.service.AgentApiKeyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentApiKeyServiceImpl extends ServiceImpl<AgentApiKeyMapper, AgentApiKey> implements AgentApiKeyService {
}
