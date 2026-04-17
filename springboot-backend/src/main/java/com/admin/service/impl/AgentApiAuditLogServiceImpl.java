package com.admin.service.impl;

import com.admin.entity.AgentApiAuditLog;
import com.admin.mapper.AgentApiAuditLogMapper;
import com.admin.service.AgentApiAuditLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentApiAuditLogServiceImpl extends ServiceImpl<AgentApiAuditLogMapper, AgentApiAuditLog> implements AgentApiAuditLogService {
}
