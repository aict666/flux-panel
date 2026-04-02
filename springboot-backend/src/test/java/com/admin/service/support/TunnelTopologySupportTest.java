package com.admin.service.support;

import com.admin.common.dto.TunnelTopologyConfigDto;
import com.admin.common.dto.TunnelTopologyItemDto;
import com.admin.entity.ChainTunnel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelTopologySupportTest {

    private final TunnelTopologySupport support = new TunnelTopologySupport();

    @Test
    void shouldBuildLegacyTopologyFromCompiledChainTunnel() {
        ChainTunnel inNode = chainTunnel(100L, 1, 11L, null, null, null, null);
        ChainTunnel hop1NodeA = chainTunnel(100L, 2, 21L, 3001, "round", 1, "tls");
        ChainTunnel hop1NodeB = chainTunnel(100L, 2, 22L, 3002, "round", 1, "tls");
        ChainTunnel hop2Node = chainTunnel(100L, 2, 31L, 3003, "fifo", 2, "tcp");
        ChainTunnel outNode = chainTunnel(100L, 3, 41L, 3004, "fifo", null, "tcp");

        TunnelTopologyConfigDto topology = support.buildLegacyTopology(List.of(
                hop2Node, outNode, inNode, hop1NodeB, hop1NodeA
        ));

        assertEquals(List.of(11L), ids(topology.getInNodeId()));
        assertEquals(2, topology.getChainNodes().size());
        assertEquals(List.of(21L, 22L), ids(topology.getChainNodes().get(0)));
        assertEquals(List.of(31L), ids(topology.getChainNodes().get(1)));
        assertEquals(List.of(41L), ids(topology.getOutNodeId()));
        assertTrue(topology.getChainNodes().get(0).stream().allMatch(item -> "node".equals(item.getItemType())));
    }

    @Test
    void shouldExpandReferencedTunnelIntoRuntimeNodeGroups() {
        TunnelTopologyConfigDto referencedTopology = topology(
                List.of(nodeItem(11L)),
                List.of(List.of(nodeHop(21L, "tls", "round"))),
                List.of(nodeHop(31L, "tcp", "fifo"))
        );

        TunnelTopologyConfigDto rootTopology = topology(
                List.of(nodeItem(1L)),
                List.of(
                        List.of(referenceHop(200L, "inner")),
                        List.of(nodeHop(41L, "mtls", "rand"))
                ),
                List.of(nodeHop(51L, "tcp", "fifo"))
        );

        TunnelTopologySupport.ExpandedTopology expanded = support.expand(
                100L,
                2,
                rootTopology,
                tunnelId -> {
                    if (tunnelId.equals(200L)) {
                        return referencedTunnel(200L, referencedTopology);
                    }
                    return null;
                }
        );

        assertEquals(List.of(1L), ids(expanded.getInNodes()));
        assertEquals(3, expanded.getChainNodes().size());
        assertEquals(List.of(21L), ids(expanded.getChainNodes().get(0)));
        assertEquals(List.of(31L), ids(expanded.getChainNodes().get(1)));
        assertEquals(List.of(41L), ids(expanded.getChainNodes().get(2)));
        assertEquals(List.of(51L), ids(expanded.getOutNodes()));
        assertEquals(Set.of(200L), expanded.getReferencedTunnelIds());
    }

    @Test
    void shouldRejectMixedNodeAndTunnelItemsInSameHop() {
        TunnelTopologyConfigDto topology = topology(
                List.of(nodeItem(1L)),
                List.of(List.of(nodeHop(21L, "tls", "round"), referenceHop(200L, "inner"))),
                List.of(nodeHop(31L, "tcp", "fifo"))
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> support.expand(
                100L,
                2,
                topology,
                tunnelId -> null
        ));

        assertTrue(error.getMessage().contains("同一跳"));
    }

    @Test
    void shouldRejectReferenceCycles() {
        TunnelTopologyConfigDto topology100 = topology(
                List.of(nodeItem(1L)),
                List.of(List.of(referenceHop(200L, "t200"))),
                List.of(nodeHop(3L, "tcp", "fifo"))
        );
        TunnelTopologyConfigDto topology200 = topology(
                List.of(nodeItem(2L)),
                List.of(List.of(referenceHop(100L, "t100"))),
                List.of(nodeHop(4L, "tcp", "fifo"))
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> support.expand(
                100L,
                2,
                topology100,
                tunnelId -> tunnelId.equals(200L) ? referencedTunnel(200L, topology200) : null
        ));

        assertTrue(error.getMessage().contains("循环"));
    }

    @Test
    void shouldCollectImpactedTunnelsAndSortReferencedFirst() {
        Map<Long, TunnelTopologyConfigDto> topologyMap = Map.of(
                100L, topology(List.of(nodeItem(1L)), List.of(), List.of(nodeHop(11L, "tcp", "fifo"))),
                200L, topology(List.of(nodeItem(2L)), List.of(List.of(referenceHop(100L, "t100"))), List.of(nodeHop(21L, "tcp", "fifo"))),
                300L, topology(List.of(nodeItem(3L)), List.of(List.of(referenceHop(200L, "t200"))), List.of(nodeHop(31L, "tcp", "fifo"))),
                400L, topology(List.of(nodeItem(4L)), List.of(), List.of(nodeHop(41L, "tcp", "fifo")))
        );

        Set<Long> impacted = support.collectImpactedTunnelIds(100L, topologyMap);
        List<Long> order = support.sortImpactedTunnelIds(impacted, topologyMap);

        assertEquals(Set.of(100L, 200L, 300L), impacted);
        assertEquals(List.of(100L, 200L, 300L), order);
    }

    private static TunnelTopologySupport.ReferencedTunnel referencedTunnel(Long id, TunnelTopologyConfigDto topology) {
        return new TunnelTopologySupport.ReferencedTunnel(id, "tunnel-" + id, 2, 1, topology);
    }

    private static ChainTunnel chainTunnel(Long tunnelId, int chainType, Long nodeId, Integer port, String strategy, Integer inx, String protocol) {
        ChainTunnel chainTunnel = new ChainTunnel();
        chainTunnel.setTunnelId(tunnelId);
        chainTunnel.setChainType(chainType);
        chainTunnel.setNodeId(nodeId);
        chainTunnel.setPort(port);
        chainTunnel.setStrategy(strategy);
        chainTunnel.setInx(inx);
        chainTunnel.setProtocol(protocol);
        return chainTunnel;
    }

    private static TunnelTopologyConfigDto topology(List<TunnelTopologyItemDto> inNodes,
                                                    List<List<TunnelTopologyItemDto>> chainNodes,
                                                    List<TunnelTopologyItemDto> outNodes) {
        TunnelTopologyConfigDto dto = new TunnelTopologyConfigDto();
        dto.setInNodeId(inNodes);
        dto.setChainNodes(chainNodes);
        dto.setOutNodeId(outNodes);
        return dto;
    }

    private static TunnelTopologyItemDto nodeItem(Long nodeId) {
        TunnelTopologyItemDto item = new TunnelTopologyItemDto();
        item.setItemType("node");
        item.setNodeId(nodeId);
        return item;
    }

    private static TunnelTopologyItemDto nodeHop(Long nodeId, String protocol, String strategy) {
        TunnelTopologyItemDto item = nodeItem(nodeId);
        item.setProtocol(protocol);
        item.setStrategy(strategy);
        return item;
    }

    private static TunnelTopologyItemDto referenceHop(Long refTunnelId, String refTunnelName) {
        TunnelTopologyItemDto item = new TunnelTopologyItemDto();
        item.setItemType("tunnel");
        item.setRefTunnelId(refTunnelId);
        item.setRefTunnelName(refTunnelName);
        return item;
    }

    private static List<Long> ids(List<TunnelTopologyItemDto> items) {
        return items.stream().map(TunnelTopologyItemDto::getNodeId).collect(Collectors.toList());
    }
}
