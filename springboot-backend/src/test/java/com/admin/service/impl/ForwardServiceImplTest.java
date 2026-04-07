package com.admin.service.impl;

import com.admin.common.dto.ForwardDto;
import com.admin.common.lang.R;
import com.admin.entity.ChainTunnel;
import com.admin.entity.Forward;
import com.admin.entity.Node;
import com.admin.entity.Tunnel;
import com.admin.service.ChainTunnelService;
import com.admin.service.ForwardPortService;
import com.admin.service.ForwardService;
import com.admin.service.NodeService;
import com.admin.service.TunnelService;
import com.admin.support.PostgresIntegrationTestSupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(properties = {
        "LOG_DIR=target/test-logs",
        "JWT_SECRET=test-secret"
})
class ForwardServiceImplTest extends PostgresIntegrationTestSupport {

    @Autowired
    private ForwardService forwardService;

    @Autowired
    private ForwardServiceImpl forwardServiceImpl;

    @Autowired
    private ForwardPortService forwardPortService;

    @Autowired
    private ChainTunnelService chainTunnelService;

    @Autowired
    private TunnelService tunnelService;

    @Autowired
    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        forwardPortService.remove(new QueryWrapper<com.admin.entity.ForwardPort>().gt("id", 0));
        forwardService.remove(new QueryWrapper<Forward>().gt("id", 0));
        chainTunnelService.remove(new QueryWrapper<ChainTunnel>().gt("id", 0));
        tunnelService.remove(new QueryWrapper<Tunnel>().gt("id", 0));
        nodeService.remove(new QueryWrapper<Node>().gt("id", 0));
        setAdminRequest();
    }

    @Test
    void shouldExcludePortsUsedBySiblingNodeOnSameHost() {
        Node firstNode = createNode("node-a", "10.0.0.1", "1000-1002");
        Node siblingNode = createNode("node-b", "10.0.0.1", "1000-1002");

        ChainTunnel siblingExit = new ChainTunnel();
        siblingExit.setTunnelId(99L);
        siblingExit.setChainType(3);
        siblingExit.setNodeId(siblingNode.getId());
        siblingExit.setPort(1000);
        siblingExit.setProtocol("tcp");
        siblingExit.setStrategy("round");
        chainTunnelService.save(siblingExit);

        List<Integer> availablePorts = forwardServiceImpl.getNodePort(firstNode.getId(), 0L);

        assertFalse(availablePorts.contains(1000));
        assertEquals(List.of(1001, 1002), availablePorts);
    }

    @Test
    void shouldReturnErrorWhenForwardServiceCreationFailsOnFirstNode() {
        Node entryNode = createNode("node-a", "10.0.0.1", "1000-1002");
        Tunnel tunnel = createTunnel("tunnel-a");

        ChainTunnel entry = new ChainTunnel();
        entry.setTunnelId(tunnel.getId());
        entry.setChainType(1);
        entry.setNodeId(entryNode.getId());
        chainTunnelService.save(entry);

        ForwardDto dto = new ForwardDto();
        dto.setName("forward-a");
        dto.setTunnelId(tunnel.getId().intValue());
        dto.setRemoteAddr("127.0.0.1:80");
        dto.setStrategy("fifo");

        R result = forwardService.createForward(dto);

        assertNotEquals(0, result.getCode());
        assertEquals("节点不在线", result.getMsg());
        assertEquals(0, forwardService.count(new QueryWrapper<>()));
        assertEquals(0, forwardPortService.count(new QueryWrapper<>()));
    }

    private Node createNode(String name, String serverIp, String portRange) {
        Node node = new Node();
        node.setName(name);
        node.setSecret(name + "-secret");
        node.setServerIp(serverIp);
        node.setPort(portRange);
        node.setInstallServiceName("flux_agent");
        node.setTcpListenAddr("0.0.0.0");
        node.setUdpListenAddr("0.0.0.0");
        node.setHttp(0);
        node.setTls(0);
        node.setSocks(0);
        node.setStatus(1);
        long now = System.currentTimeMillis();
        node.setCreatedTime(now);
        node.setUpdatedTime(now);
        nodeService.save(node);
        return node;
    }

    private Tunnel createTunnel(String name) {
        Tunnel tunnel = new Tunnel();
        tunnel.setName(name);
        tunnel.setType(2);
        tunnel.setFlow(1);
        tunnel.setTrafficRatio(java.math.BigDecimal.ONE);
        tunnel.setStatus(1);
        tunnel.setCreatedTime(System.currentTimeMillis());
        tunnel.setUpdatedTime(System.currentTimeMillis());
        tunnelService.save(tunnel);
        return tunnel;
    }

    private void setAdminRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", buildToken(1, 0, "admin_user"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private String buildToken(int userId, int roleId, String name) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = String.format("{\"sub\":\"%d\",\"role_id\":%d,\"name\":\"%s\"}", userId, roleId, name);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return header + "." + encodedPayload + ".sig";
    }
}
