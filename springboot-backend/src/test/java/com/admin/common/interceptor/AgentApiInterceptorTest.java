package com.admin.common.interceptor;

import com.admin.common.context.ActorContext;
import com.admin.common.context.ActorContextHolder;
import com.admin.common.context.ActorType;
import com.admin.common.exception.UnauthorizedException;
import com.admin.common.utils.AgentKeyUtil;
import com.admin.entity.AgentApiKey;
import com.admin.entity.AgentApiAuditLog;
import com.admin.entity.AgentClient;
import com.admin.service.AgentApiAuditLogService;
import com.admin.service.AgentApiKeyService;
import com.admin.service.AgentClientService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentApiInterceptorTest {

    @AfterEach
    void tearDown() {
        ActorContextHolder.clear();
    }

    @Test
    void preHandleShouldAuthenticateAgentKeyAndPopulateContext() throws Exception {
        String rawKey = "fpak_prefixabc123_secretsecretsecretsecret1234";
        AgentApiKeyService keyService = Mockito.mock(AgentApiKeyService.class);
        AgentClientService clientService = Mockito.mock(AgentClientService.class);
        AgentApiAuditLogService auditLogService = Mockito.mock(AgentApiAuditLogService.class);

        AgentApiKey apiKey = new AgentApiKey();
        apiKey.setId(3L);
        apiKey.setClientId(8L);
        apiKey.setKeyPrefix("prefixabc123");
        apiKey.setKeyHash(AgentKeyUtil.hashKey(rawKey));
        apiKey.setStatus(1);
        apiKey.setExpiresTime(System.currentTimeMillis() + 60_000L);

        AgentClient client = new AgentClient();
        client.setId(8L);
        client.setName("openclaw-admin");
        client.setScopeJson("[\"forwards:read\",\"stats:read\"]");
        client.setStatus(1);

        Mockito.when(keyService.getOne(Mockito.any(QueryWrapper.class))).thenReturn(apiKey);
        Mockito.when(clientService.getById(8L)).thenReturn(client);

        AgentApiInterceptor interceptor = new AgentApiInterceptor(keyService, clientService, auditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/forwards/list");
        request.addHeader("Authorization", "Bearer " + rawKey);
        request.setRemoteAddr("127.0.0.1");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        ActorContext context = ActorContextHolder.get();
        assertEquals(ActorType.AGENT, context.getActorType());
        assertEquals(8L, context.getClientId());
        assertEquals("openclaw-admin", context.getClientName());
        assertEquals(Set.of("forwards:read", "stats:read"), context.getScopes());
        Mockito.verify(keyService).updateById(Mockito.argThat(updated ->
                updated.getId().equals(3L) &&
                        updated.getLastUsedTime() != null &&
                        "127.0.0.1".equals(updated.getLastUsedIp())
        ));
    }

    @Test
    void preHandleShouldRejectInvalidAgentKey() {
        AgentApiKeyService keyService = Mockito.mock(AgentApiKeyService.class);
        AgentClientService clientService = Mockito.mock(AgentClientService.class);
        AgentApiAuditLogService auditLogService = Mockito.mock(AgentApiAuditLogService.class);

        Mockito.when(keyService.getOne(Mockito.any(QueryWrapper.class))).thenReturn(null);

        AgentApiInterceptor interceptor = new AgentApiInterceptor(keyService, clientService, auditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/forwards/list");
        request.addHeader("Authorization", "Bearer fpak_prefixabc123_secretsecretsecretsecret1234");

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void afterCompletionShouldWriteAuditLogForSuccessfulRequests() throws Exception {
        String rawKey = "fpak_prefixabc123_secretsecretsecretsecret1234";
        AgentApiKeyService keyService = Mockito.mock(AgentApiKeyService.class);
        AgentClientService clientService = Mockito.mock(AgentClientService.class);
        AgentApiAuditLogService auditLogService = Mockito.mock(AgentApiAuditLogService.class);

        AgentApiKey apiKey = new AgentApiKey();
        apiKey.setId(3L);
        apiKey.setClientId(8L);
        apiKey.setKeyPrefix("prefixabc123");
        apiKey.setKeyHash(AgentKeyUtil.hashKey(rawKey));
        apiKey.setStatus(1);
        apiKey.setExpiresTime(System.currentTimeMillis() + 60_000L);

        AgentClient client = new AgentClient();
        client.setId(8L);
        client.setName("openclaw-admin");
        client.setScopeJson("[\"forwards:read\"]");
        client.setStatus(1);

        Mockito.when(keyService.getOne(Mockito.any(QueryWrapper.class))).thenReturn(apiKey);
        Mockito.when(clientService.getById(8L)).thenReturn(client);

        AgentApiInterceptor interceptor = new AgentApiInterceptor(keyService, clientService, auditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/forwards/list");
        request.addHeader("Authorization", "Bearer " + rawKey);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<AgentApiAuditLog> captor = ArgumentCaptor.forClass(AgentApiAuditLog.class);
        Mockito.verify(auditLogService).save(captor.capture());
        AgentApiAuditLog auditLog = captor.getValue();
        assertEquals(8L, auditLog.getClientId());
        assertEquals("openclaw-admin", auditLog.getClientName());
        assertEquals("/api/v1/agent/forwards/list", auditLog.getRequestPath());
        assertEquals("POST", auditLog.getHttpMethod());
        assertEquals(200, auditLog.getStatusCode());
        assertTrue(auditLog.getSuccess());
    }
}
