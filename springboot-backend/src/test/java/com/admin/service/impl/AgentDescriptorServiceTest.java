package com.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDescriptorServiceTest {

    private final AgentDescriptorServiceImpl service = new AgentDescriptorServiceImpl();

    @Test
    void openclawDescriptorShouldOnlyContainAuthorizedTools() {
        Map<String, Object> descriptor = service.buildDescriptor("openclaw", Set.of("forwards:read", "stats:read"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) descriptor.get("tools");

        assertEquals("openclaw", descriptor.get("format"));
        assertTrue(tools.stream().anyMatch(tool -> "list_forwards".equals(tool.get("name"))));
        assertTrue(tools.stream().anyMatch(tool -> "query_stats_series".equals(tool.get("name"))));
        assertFalse(tools.stream().anyMatch(tool -> "create_user".equals(tool.get("name"))));
    }

    @Test
    void hermesDescriptorShouldUseCapabilitiesField() {
        Map<String, Object> descriptor = service.buildDescriptor("hermes-agent", Set.of("users:read"));

        assertEquals("hermes-agent", descriptor.get("format"));
        assertTrue(descriptor.containsKey("capabilities"));
        assertFalse(descriptor.containsKey("tools"));
    }
}
