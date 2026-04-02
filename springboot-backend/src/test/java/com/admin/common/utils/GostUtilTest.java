package com.admin.common.utils;

import com.admin.entity.ChainTunnel;
import com.admin.entity.Node;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GostUtilTest {

    @Test
    void createChainDataShouldAvoidNodeNullWhenHopIndexMissing() {
        ChainTunnel outNode = new ChainTunnel();
        outNode.setTunnelId(2L);
        outNode.setChainType(3);
        outNode.setNodeId(2L);
        outNode.setPort(1000);
        outNode.setStrategy("round");
        outNode.setProtocol("tcp");

        Node currentNode = new Node();
        currentNode.setId(1L);
        currentNode.setInterfaceName("eth0");

        Node targetNode = new Node();
        targetNode.setId(2L);
        targetNode.setServerIp("192.168.31.8");

        JSONObject data = GostUtil.createChainData(1L, List.of(outNode), Map.of(
                1L, currentNode,
                2L, targetNode
        ));

        JSONArray nodes = data.getJSONArray("hops").getJSONObject(0).getJSONArray("nodes");
        assertEquals("node_2", nodes.getJSONObject(0).getString("name"));
    }

    @Test
    void createChainDataShouldUseUniqueNamesForSameHopNodes() {
        ChainTunnel firstNode = new ChainTunnel();
        firstNode.setTunnelId(2L);
        firstNode.setChainType(2);
        firstNode.setNodeId(2L);
        firstNode.setPort(1000);
        firstNode.setStrategy("round");
        firstNode.setProtocol("tcp");
        firstNode.setInx(1);

        ChainTunnel secondNode = new ChainTunnel();
        secondNode.setTunnelId(2L);
        secondNode.setChainType(2);
        secondNode.setNodeId(3L);
        secondNode.setPort(1001);
        secondNode.setStrategy("round");
        secondNode.setProtocol("tcp");
        secondNode.setInx(1);

        Node currentNode = new Node();
        currentNode.setId(1L);

        Node firstTarget = new Node();
        firstTarget.setId(2L);
        firstTarget.setServerIp("192.168.31.8");

        Node secondTarget = new Node();
        secondTarget.setId(3L);
        secondTarget.setServerIp("192.168.31.9");

        JSONObject data = GostUtil.createChainData(1L, List.of(firstNode, secondNode), Map.of(
                1L, currentNode,
                2L, firstTarget,
                3L, secondTarget
        ));

        JSONArray nodes = data.getJSONArray("hops").getJSONObject(0).getJSONArray("nodes");
        assertEquals("node_1_2", nodes.getJSONObject(0).getString("name"));
        assertEquals("node_1_3", nodes.getJSONObject(1).getString("name"));
    }
}
