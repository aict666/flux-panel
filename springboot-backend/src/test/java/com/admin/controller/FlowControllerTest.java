package com.admin.controller;

import com.admin.entity.Forward;
import com.admin.entity.Node;
import com.admin.entity.Tunnel;
import com.admin.entity.User;
import com.admin.entity.UserTunnel;
import com.admin.service.ForwardService;
import com.admin.service.NodeService;
import com.admin.service.TunnelService;
import com.admin.service.UserService;
import com.admin.service.UserTunnelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowControllerTest {

    @Test
    void shouldSkipNonForwardServiceNamesAndStillProcessValidFlowItems() {
        FlowController controller = new FlowController();
        controller.nodeService = Mockito.mock(NodeService.class);
        controller.forwardService = Mockito.mock(ForwardService.class);
        controller.userService = Mockito.mock(UserService.class);
        controller.userTunnelService = Mockito.mock(UserTunnelService.class);
        controller.tunnelService = Mockito.mock(TunnelService.class);

        when(controller.nodeService.count(ArgumentMatchers.any())).thenReturn(1);

        Forward forward = new Forward();
        forward.setId(15L);
        forward.setTunnelId(2);
        when(controller.forwardService.getById(15L)).thenReturn(forward);

        Tunnel tunnel = new Tunnel();
        tunnel.setId(2L);
        tunnel.setTrafficRatio(BigDecimal.ONE);
        tunnel.setFlow(1);
        when(controller.tunnelService.getById(2)).thenReturn(tunnel);

        User user = new User();
        user.setId(7L);
        user.setFlow(99999L);
        user.setInFlow(0L);
        user.setOutFlow(0L);
        user.setStatus(1);
        user.setExpTime(System.currentTimeMillis() + 60_000L);
        when(controller.userService.getById(7L)).thenReturn(user);

        UserTunnel userTunnel = new UserTunnel();
        userTunnel.setId(9);
        userTunnel.setTunnelId(2);
        userTunnel.setFlow(99999L);
        userTunnel.setInFlow(0L);
        userTunnel.setOutFlow(0L);
        userTunnel.setStatus(1);
        userTunnel.setExpTime(System.currentTimeMillis() + 60_000L);
        when(controller.userTunnelService.getById(9L)).thenReturn(userTunnel);

        String rawData = """
                [
                  {"n":"2_tls","u":5,"d":5},
                  {"n":"15_7_9_tcp","u":10,"d":20}
                ]
                """;

        String result = controller.uploadFlowData(rawData, "secret-a");

        assertEquals("ok", result);
        verify(controller.forwardService, times(1)).getById(15L);
        verify(controller.forwardService, times(1)).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
        verify(controller.userService, times(1)).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
        verify(controller.userTunnelService, times(1)).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
        verify(controller.userService, times(1)).getById(7L);
        verify(controller.userTunnelService, times(1)).getById(9L);
    }

    @Test
    void shouldReturnOkWhenBatchContainsOnlyChainServiceTraffic() {
        FlowController controller = new FlowController();
        controller.nodeService = Mockito.mock(NodeService.class);
        controller.forwardService = Mockito.mock(ForwardService.class);
        controller.userService = Mockito.mock(UserService.class);
        controller.userTunnelService = Mockito.mock(UserTunnelService.class);
        controller.tunnelService = Mockito.mock(TunnelService.class);

        when(controller.nodeService.count(ArgumentMatchers.any())).thenReturn(1);

        String result = controller.uploadFlowData("[{\"n\":\"2_tls\",\"u\":5,\"d\":5}]", "secret-a");

        assertEquals("ok", result);
        verify(controller.forwardService, never()).getById(ArgumentMatchers.any());
        verify(controller.forwardService, never()).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
        verify(controller.userService, never()).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
        verify(controller.userTunnelService, never()).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
    }
}
