package com.admin.common.task;

import com.admin.common.dto.ConfigItem;
import com.admin.common.dto.GostConfigDto;
import com.admin.entity.Forward;
import com.admin.entity.Node;
import com.admin.entity.SpeedLimit;
import com.admin.entity.Tunnel;
import com.admin.service.ForwardService;
import com.admin.service.NodeService;
import com.admin.service.SpeedLimitService;
import com.admin.service.TunnelService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CheckGostConfigAsyncTest {

    @Test
    void shouldParseRuntimeNamesIntoNumericIdsAndSkipInvalidEntries() {
        CheckGostConfigAsync task = new CheckGostConfigAsync();
        NodeService nodeService = Mockito.mock(NodeService.class);
        ForwardService forwardService = Mockito.mock(ForwardService.class);
        SpeedLimitService speedLimitService = Mockito.mock(SpeedLimitService.class);
        TunnelService tunnelService = Mockito.mock(TunnelService.class);

        ReflectionTestUtils.setField(task, "nodeService", nodeService);
        ReflectionTestUtils.setField(task, "forwardService", forwardService);
        ReflectionTestUtils.setField(task, "speedLimitService", speedLimitService);
        ReflectionTestUtils.setField(task, "tunnelService", tunnelService);

        Node node = new Node();
        node.setId(9L);
        when(nodeService.getById(9L)).thenReturn(node);
        when(tunnelService.getById(2L)).thenReturn(new Tunnel());
        when(forwardService.getById(15L)).thenReturn(new Forward());
        when(tunnelService.getById(29L)).thenReturn(new Tunnel());
        when(speedLimitService.getById(123L)).thenReturn(new SpeedLimit());

        GostConfigDto config = new GostConfigDto();
        config.setServices(List.of(item("2_tls"), item("15_7_9_tcp"), item("bad_tls"), item("abc_1_0_tcp"), item("web_api")));
        config.setChains(List.of(item("chains_29"), item("chains_invalid"), item("invalid-chain")));
        config.setLimiters(List.of(item("123"), item("limiter_bad")));

        task.cleanNodeConfigs(9L, config);

        verify(nodeService).getById(9L);
        verify(tunnelService).getById(2L);
        verify(forwardService).getById(15L);
        verify(tunnelService).getById(29L);
        verify(speedLimitService).getById(123L);
        verifyNoMoreInteractions(nodeService, forwardService, speedLimitService, tunnelService);
    }

    private ConfigItem item(String name) {
        ConfigItem item = new ConfigItem();
        item.setName(name);
        return item;
    }
}
