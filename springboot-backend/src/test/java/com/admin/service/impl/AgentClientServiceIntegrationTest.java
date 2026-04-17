package com.admin.service.impl;

import com.admin.common.dto.AgentClientDto;
import com.admin.common.dto.AgentClientUpdateDto;
import com.admin.common.dto.AgentKeyRotateDto;
import com.admin.common.lang.R;
import com.admin.entity.AgentApiKey;
import com.admin.entity.AgentClient;
import com.admin.service.AgentApiKeyService;
import com.admin.service.AgentClientService;
import com.admin.support.PostgresIntegrationTestSupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "LOG_DIR=target/test-logs",
        "JWT_SECRET=test-secret"
})
class AgentClientServiceIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private AgentClientService agentClientService;

    @Autowired
    private AgentApiKeyService agentApiKeyService;

    @BeforeEach
    void setUp() {
        agentApiKeyService.remove(new QueryWrapper<>());
        agentClientService.remove(new QueryWrapper<>());
    }

    @Test
    void createClientShouldPersistClientAndReturnPlaintextKey() {
        AgentClientDto dto = new AgentClientDto();
        dto.setName("claw-admin");
        dto.setAgentType("openclaw");
        dto.setDescription("OpenClaw 管理账号");
        dto.setScopes(Set.of("forwards:read", "stats:read"));
        dto.setExpiresTime(System.currentTimeMillis() + 86_400_000L);

        R result = agentClientService.createClient(dto);

        assertEquals(0, result.getCode());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertTrue(data.get("plaintextKey").toString().startsWith("fpak_"));

        AgentClient client = agentClientService.getOne(new QueryWrapper<AgentClient>().eq("name", "claw-admin"));
        assertNotNull(client);
        assertEquals("openclaw", client.getAgentType());
        assertTrue(client.getScopeJson().contains("forwards:read"));

        List<AgentApiKey> keys = agentApiKeyService.list(new QueryWrapper<AgentApiKey>().eq("client_id", client.getId()));
        assertEquals(1, keys.size());
        assertEquals(1, keys.getFirst().getStatus());
    }

    @Test
    void rotateKeyShouldRevokePreviousKeysAndReturnNewPlaintextKey() {
        Long clientId = createClient("rotate-client");
        AgentApiKey originalKey = agentApiKeyService.getOne(new QueryWrapper<AgentApiKey>().eq("client_id", clientId));

        AgentKeyRotateDto dto = new AgentKeyRotateDto();
        dto.setExpiresTime(System.currentTimeMillis() + 172_800_000L);

        R result = agentClientService.rotateKey(clientId, dto);

        assertEquals(0, result.getCode());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertTrue(data.get("plaintextKey").toString().startsWith("fpak_"));

        AgentApiKey revokedKey = agentApiKeyService.getById(originalKey.getId());
        assertEquals(0, revokedKey.getStatus());

        List<AgentApiKey> keys = agentApiKeyService.list(new QueryWrapper<AgentApiKey>().eq("client_id", clientId));
        assertEquals(2, keys.size());
        assertEquals(1L, keys.stream().filter(key -> key.getStatus() == 1).count());
    }

    @Test
    void deleteClientShouldSoftDeleteClientAndRevokeAllKeys() {
        Long clientId = createClient("delete-client");

        R result = agentClientService.deleteClient(clientId);

        assertEquals(0, result.getCode());

        AgentClient client = agentClientService.getById(clientId);
        assertNotNull(client);
        assertEquals(0, client.getStatus());

        List<AgentApiKey> keys = agentApiKeyService.list(new QueryWrapper<AgentApiKey>().eq("client_id", clientId));
        assertFalse(keys.isEmpty());
        assertTrue(keys.stream().allMatch(key -> key.getStatus() == 0));
    }

    @Test
    void updateClientShouldChangeMetadataAndScopes() {
        Long clientId = createClient("update-client");

        AgentClientUpdateDto dto = new AgentClientUpdateDto();
        dto.setId(clientId);
        dto.setName("update-client-2");
        dto.setDescription("更新描述");
        dto.setStatus(0);
        dto.setScopes(Set.of("users:read", "users:write"));

        R result = agentClientService.updateClient(dto);

        assertEquals(0, result.getCode());

        AgentClient client = agentClientService.getById(clientId);
        assertEquals("update-client-2", client.getName());
        assertEquals("更新描述", client.getDescription());
        assertEquals(0, client.getStatus());
        assertTrue(client.getScopeJson().contains("users:write"));
    }

    private Long createClient(String name) {
        AgentClientDto dto = new AgentClientDto();
        dto.setName(name);
        dto.setAgentType("openclaw");
        dto.setDescription("测试账号");
        dto.setScopes(Set.of("forwards:read"));
        dto.setExpiresTime(System.currentTimeMillis() + 86_400_000L);
        R result = agentClientService.createClient(dto);
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        Object clientId = data.get("clientId");
        return Long.parseLong(clientId.toString());
    }
}
