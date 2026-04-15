package com.admin.service.impl;

import com.admin.common.dto.TunnelTopologyConfigDto;
import com.admin.common.dto.TunnelTopologyItemDto;
import com.admin.common.lang.R;
import com.admin.entity.ChainTunnel;
import com.admin.entity.Forward;
import com.admin.entity.ForwardPort;
import com.admin.entity.Node;
import com.admin.entity.SpeedLimit;
import com.admin.entity.Tunnel;
import com.admin.entity.UserTunnel;
import com.admin.service.ChainTunnelService;
import com.admin.service.ForwardPortService;
import com.admin.service.ForwardService;
import com.admin.service.NodeService;
import com.admin.service.SpeedLimitService;
import com.admin.service.TunnelService;
import com.admin.service.UserTunnelService;
import com.admin.service.support.TunnelTopologySupport;
import com.admin.support.PostgresIntegrationTestSupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(properties = {
        "LOG_DIR=target/test-logs",
        "JWT_SECRET=test-secret"
})
class NodeServiceDeleteCascadeTest extends PostgresIntegrationTestSupport {

    private final TunnelTopologySupport topologySupport = new TunnelTopologySupport();

    @Autowired
    private NodeService nodeService;

    @Autowired
    private TunnelService tunnelService;

    @Autowired
    private ChainTunnelService chainTunnelService;

    @Autowired
    private ForwardService forwardService;

    @Autowired
    private ForwardPortService forwardPortService;

    @Autowired
    private SpeedLimitService speedLimitService;

    @Autowired
    private UserTunnelService userTunnelService;

    @BeforeEach
    void setUp() {
        forwardPortService.remove(new QueryWrapper<ForwardPort>().gt("id", 0));
        forwardService.remove(new QueryWrapper<Forward>().gt("id", 0));
        speedLimitService.remove(new QueryWrapper<SpeedLimit>().gt("id", 0));
        userTunnelService.remove(new QueryWrapper<UserTunnel>().gt("id", 0));
        chainTunnelService.remove(new QueryWrapper<ChainTunnel>().gt("id", 0));
        tunnelService.remove(new QueryWrapper<Tunnel>().gt("id", 0));
        nodeService.remove(new QueryWrapper<Node>().gt("id", 0));
    }

    @Test
    void shouldDeleteNodeAndDirectlyRelatedTunnelWithoutPostgresGroupByError() {
        Node node = saveNode("node-direct");
        Tunnel tunnel = saveTunnel("direct-tunnel", null);
        saveChainTunnel(tunnel.getId(), 1, node.getId());

        R result = assertDoesNotThrow(() -> nodeService.deleteNode(node.getId()));

        assertEquals(0, result.getCode());
        assertNull(nodeService.getById(node.getId()));
        assertNull(tunnelService.getById(tunnel.getId()));
        assertEquals(0, chainTunnelService.count(new QueryWrapper<ChainTunnel>().eq("tunnel_id", tunnel.getId())));
    }

    @Test
    void shouldCascadeDeleteDependentTunnelsReferencedByTopologyJson() {
        Node node = saveNode("node-cascade");
        Tunnel baseTunnel = saveTunnel("base-tunnel", null);
        saveChainTunnel(baseTunnel.getId(), 1, node.getId());

        Tunnel dependentTunnel = saveTunnel("dependent-tunnel", referencedTunnelTopology(baseTunnel.getId()));

        R result = assertDoesNotThrow(() -> nodeService.deleteNode(node.getId()));

        assertEquals(0, result.getCode());
        assertNull(nodeService.getById(node.getId()));
        assertNull(tunnelService.getById(baseTunnel.getId()));
        assertNull(tunnelService.getById(dependentTunnel.getId()));
    }

    @Test
    void shouldDeleteNodeSuccessfullyWhenMultipleChainRowsPointToSameTunnel() {
        Node node = saveNode("node-duplicate");
        Tunnel tunnel = saveTunnel("duplicate-tunnel", null);
        saveChainTunnel(tunnel.getId(), 1, node.getId());
        saveChainTunnel(tunnel.getId(), 3, node.getId());

        R result = assertDoesNotThrow(() -> nodeService.deleteNode(node.getId()));

        assertEquals(0, result.getCode());
        assertNull(nodeService.getById(node.getId()));
        assertNull(tunnelService.getById(tunnel.getId()));
    }

    private Node saveNode(String name) {
        long now = System.currentTimeMillis();
        Node node = new Node();
        node.setName(name);
        node.setSecret(name + "-secret");
        node.setServerIp("10.0.0.1");
        node.setPort("1000-2000");
        node.setInstallServiceName("flux_agent");
        node.setHttp(0);
        node.setTls(0);
        node.setSocks(0);
        node.setStatus(0);
        node.setCreatedTime(now);
        node.setUpdatedTime(now);
        node.setTcpListenAddr("[::]");
        node.setUdpListenAddr("[::]");
        nodeService.save(node);
        return node;
    }

    private Tunnel saveTunnel(String name, String topologyJson) {
        long now = System.currentTimeMillis();
        Tunnel tunnel = new Tunnel();
        tunnel.setName(name);
        tunnel.setType(2);
        tunnel.setProtocol("tls");
        tunnel.setFlow(1);
        tunnel.setTrafficRatio(BigDecimal.ONE);
        tunnel.setStatus(1);
        tunnel.setCreatedTime(now);
        tunnel.setUpdatedTime(now);
        tunnel.setTopologyJson(topologyJson);
        tunnelService.save(tunnel);
        return tunnel;
    }

    private void saveChainTunnel(Long tunnelId, Integer chainType, Long nodeId) {
        ChainTunnel chainTunnel = new ChainTunnel();
        chainTunnel.setTunnelId(tunnelId);
        chainTunnel.setChainType(chainType);
        chainTunnel.setNodeId(nodeId);
        chainTunnel.setProtocol("tcp");
        chainTunnelService.save(chainTunnel);
    }

    private String referencedTunnelTopology(Long referencedTunnelId) {
        TunnelTopologyItemDto reference = new TunnelTopologyItemDto();
        reference.setItemType("tunnel");
        reference.setRefTunnelId(referencedTunnelId);
        reference.setRefTunnelName("ref-" + referencedTunnelId);

        TunnelTopologyConfigDto topology = new TunnelTopologyConfigDto();
        topology.setChainNodes(List.of(List.of(reference)));
        return topologySupport.serializeTopology(topology);
    }
}
