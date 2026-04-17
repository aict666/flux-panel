package com.admin.common.interceptor;

import com.admin.common.context.ActorContext;
import com.admin.common.context.ActorContextHolder;
import com.admin.common.context.ActorType;
import com.admin.common.exception.UnauthorizedException;
import com.admin.common.utils.AgentKeyUtil;
import com.admin.common.utils.AgentScopeGuard;
import com.admin.entity.AgentApiAuditLog;
import com.admin.entity.AgentApiKey;
import com.admin.entity.AgentClient;
import com.admin.service.AgentApiAuditLogService;
import com.admin.service.AgentApiKeyService;
import com.admin.service.AgentClientService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AgentApiInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "agent_api_start_time";
    private static final String ACTION_ATTR = "agent_api_action";

    private final AgentApiKeyService agentApiKeyService;
    private final AgentClientService agentClientService;
    private final AgentApiAuditLogService agentApiAuditLogService;

    public AgentApiInterceptor(
            AgentApiKeyService agentApiKeyService,
            AgentClientService agentClientService,
            AgentApiAuditLogService agentApiAuditLogService
    ) {
        this.agentApiKeyService = agentApiKeyService;
        this.agentClientService = agentClientService;
        this.agentApiAuditLogService = agentApiAuditLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            saveRejectedAudit(request, null, null, "缺少有效的 Agent Key", 401);
            throw new UnauthorizedException("缺少有效的 Agent Key");
        }

        String rawKey = authorization.substring("Bearer ".length()).trim();
        String prefix;
        try {
            prefix = AgentKeyUtil.extractPrefix(rawKey);
        } catch (IllegalArgumentException exception) {
            saveRejectedAudit(request, null, null, exception.getMessage(), 401);
            throw new UnauthorizedException("无效的 Agent Key");
        }

        AgentApiKey apiKey = agentApiKeyService.getOne(
                new QueryWrapper<AgentApiKey>()
                        .eq("key_prefix", prefix)
                        .last("limit 1")
        );
        if (apiKey == null || apiKey.getStatus() == null || apiKey.getStatus() != 1) {
            saveRejectedAudit(request, null, null, "无效的 Agent Key", 401);
            throw new UnauthorizedException("无效的 Agent Key");
        }
        if (!AgentKeyUtil.hashKey(rawKey).equals(apiKey.getKeyHash())) {
            saveRejectedAudit(request, apiKey.getClientId(), null, "无效的 Agent Key", 401);
            throw new UnauthorizedException("无效的 Agent Key");
        }
        if (apiKey.getExpiresTime() != null && apiKey.getExpiresTime() <= System.currentTimeMillis()) {
            saveRejectedAudit(request, apiKey.getClientId(), null, "Agent Key 已过期", 401);
            throw new UnauthorizedException("Agent Key 已过期");
        }

        AgentClient client = agentClientService.getById(apiKey.getClientId());
        if (client == null || client.getStatus() == null || client.getStatus() != 1) {
            saveRejectedAudit(request, apiKey.getClientId(), client == null ? null : client.getName(), "服务账号不可用", 401);
            throw new UnauthorizedException("服务账号不可用");
        }

        Set<String> scopes = new HashSet<>(JSON.parseArray(client.getScopeJson(), String.class));
        ActorContextHolder.set(new ActorContext(
                ActorType.AGENT,
                null,
                null,
                null,
                client.getId(),
                client.getName(),
                scopes
        ));

        AgentApiKey update = new AgentApiKey();
        update.setId(apiKey.getId());
        update.setLastUsedTime(System.currentTimeMillis());
        update.setLastUsedIp(request.getRemoteAddr());
        update.setUpdatedTime(System.currentTimeMillis());
        agentApiKeyService.updateById(update);

        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        request.setAttribute(ACTION_ATTR, buildAction(request.getRequestURI()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            ActorContext actorContext = ActorContextHolder.get();
            if (actorContext != null && actorContext.isAgent()) {
                Integer deniedStatus = (Integer) request.getAttribute(AgentScopeGuard.DENIED_STATUS_ATTR);
                String deniedMessage = (String) request.getAttribute(AgentScopeGuard.DENIED_MESSAGE_ATTR);
                AgentApiAuditLog auditLog = new AgentApiAuditLog();
                auditLog.setClientId(actorContext.getClientId());
                auditLog.setClientName(actorContext.getClientName());
                auditLog.setRequestPath(request.getRequestURI());
                auditLog.setHttpMethod(request.getMethod());
                auditLog.setAction(String.valueOf(request.getAttribute(ACTION_ATTR)));
                auditLog.setStatusCode(deniedStatus != null ? deniedStatus : response.getStatus());
                auditLog.setSuccess(ex == null && deniedStatus == null && response.getStatus() < 400);
                auditLog.setDurationMs(resolveDuration(request));
                auditLog.setRequestIp(request.getRemoteAddr());
                auditLog.setErrorMessage(deniedMessage != null ? deniedMessage : ex == null ? null : ex.getMessage());
                auditLog.setCreatedTime(System.currentTimeMillis());
                auditLog.setUpdatedTime(System.currentTimeMillis());
                auditLog.setStatus(1);
                agentApiAuditLogService.save(auditLog);
            }
        } finally {
            ActorContextHolder.clear();
        }
    }

    private long resolveDuration(HttpServletRequest request) {
        Object startTime = request.getAttribute(START_TIME_ATTR);
        if (startTime instanceof Long value) {
            return Math.max(0, System.currentTimeMillis() - value);
        }
        return 0L;
    }

    private String buildAction(String requestUri) {
        List<String> parts = List.of(requestUri.split("/"));
        if (parts.size() >= 2) {
            String operation = parts.get(parts.size() - 1);
            String resource = parts.size() >= 3 ? parts.get(parts.size() - 2) : "agent";
            return resource + "." + operation;
        }
        return "agent.unknown";
    }

    private void saveRejectedAudit(HttpServletRequest request, Long clientId, String clientName, String message, int statusCode) {
        AgentApiAuditLog auditLog = new AgentApiAuditLog();
        auditLog.setClientId(clientId);
        auditLog.setClientName(clientName);
        auditLog.setRequestPath(request.getRequestURI());
        auditLog.setHttpMethod(request.getMethod());
        auditLog.setAction(buildAction(request.getRequestURI()));
        auditLog.setStatusCode(statusCode);
        auditLog.setSuccess(false);
        auditLog.setDurationMs(0L);
        auditLog.setRequestIp(request.getRemoteAddr());
        auditLog.setErrorMessage(message);
        auditLog.setCreatedTime(System.currentTimeMillis());
        auditLog.setUpdatedTime(System.currentTimeMillis());
        auditLog.setStatus(1);
        agentApiAuditLogService.save(auditLog);
    }
}
