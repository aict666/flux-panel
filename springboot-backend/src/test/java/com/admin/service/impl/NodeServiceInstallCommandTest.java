package com.admin.service.impl;

import com.admin.common.dto.NodeDto;
import com.admin.common.dto.NodeInstallCommandDto;
import com.admin.common.dto.NodeUpdateDto;
import com.admin.common.lang.R;
import com.admin.entity.Node;
import com.admin.entity.ViteConfig;
import com.admin.service.ViteConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeServiceInstallCommandTest {

    @Test
    void shouldBuildInstallUpdateAndUninstallCommandsWithConfiguredServiceName() {
        TestableNodeServiceImpl service = new TestableNodeServiceImpl();
        service.node = buildNode(1L, "secret-a", "agent_a");
        service.viteConfigService = mockIpConfigService("panel.example.com:6365");

        R result = service.getInstallCommand(1L);

        NodeInstallCommandDto dto = assertInstanceOf(NodeInstallCommandDto.class, result.getData());
        assertEquals("agent_a", dto.getServiceName());
        assertTrue(dto.getInstallCommand().contains("https://github.com/aict666/flux-panel/releases/latest/download/install.sh"));
        assertTrue(dto.getInstallCommand().contains("./install.sh -n agent_a -a panel.example.com:6365 -s secret-a"));
        assertTrue(dto.getUpdateCommand().contains("./install.sh --update -n agent_a"));
        assertTrue(dto.getUninstallCommand().contains("./install.sh --uninstall -n agent_a"));
    }

    @Test
    void shouldFallbackToFluxAgentWhenInstallServiceNameMissing() {
        TestableNodeServiceImpl service = new TestableNodeServiceImpl();
        service.node = buildNode(2L, "secret-b", null);
        service.viteConfigService = mockIpConfigService("192.168.1.10:6365");

        R result = service.getInstallCommand(2L);

        NodeInstallCommandDto dto = assertInstanceOf(NodeInstallCommandDto.class, result.getData());
        assertEquals("flux_agent", dto.getServiceName());
        assertTrue(dto.getInstallCommand().contains("./install.sh -n flux_agent -a 192.168.1.10:6365 -s secret-b"));
    }

    @Test
    void shouldStripProtocolAndPathFromConfiguredPanelAddress() {
        TestableNodeServiceImpl service = new TestableNodeServiceImpl();
        service.node = buildNode(4L, "secret-d", "agent_d");
        service.viteConfigService = mockIpConfigService("http://panel.example.com:6365/flow/upload");

        R result = service.getInstallCommand(4L);

        NodeInstallCommandDto dto = assertInstanceOf(NodeInstallCommandDto.class, result.getData());
        assertTrue(dto.getInstallCommand().contains("./install.sh -n agent_d -a panel.example.com:6365 -s secret-d"));
    }

    @Test
    void shouldPersistNormalizedServiceNameOnCreateAndUpdate() {
        TestableNodeServiceImpl service = new TestableNodeServiceImpl();

        NodeDto createDto = new NodeDto();
        createDto.setName("node-a");
        createDto.setServerIp("1.1.1.1");
        createDto.setPort("1000-2000");
        createDto.setInstallServiceName("agent_a");
        service.createNode(createDto);

        assertEquals("agent_a", service.savedNode.getInstallServiceName());

        Node existing = buildNode(3L, "secret-c", "old_agent");
        existing.setStatus(0);
        service.node = existing;

        NodeUpdateDto updateDto = new NodeUpdateDto();
        updateDto.setId(3L);
        updateDto.setName("node-a");
        updateDto.setServerIp("1.1.1.2");
        updateDto.setPort("3000-4000");
        updateDto.setInstallServiceName("");
        service.updateNode(updateDto);

        assertEquals("flux_agent", service.updatedNode.getInstallServiceName());
    }

    private static ViteConfigService mockIpConfigService(String addr) {
        ViteConfigService viteConfigService = Mockito.mock(ViteConfigService.class);
        ViteConfig viteConfig = new ViteConfig();
        viteConfig.setName("ip");
        viteConfig.setValue(addr);
        Mockito.when(viteConfigService.getOne(Mockito.any())).thenReturn(viteConfig);
        return viteConfigService;
    }

    private static Node buildNode(Long id, String secret, String installServiceName) {
        Node node = new Node();
        node.setId(id);
        node.setSecret(secret);
        node.setInstallServiceName(installServiceName);
        return node;
    }

    private static class TestableNodeServiceImpl extends NodeServiceImpl {
        private Node node;
        private Node savedNode;
        private Node updatedNode;

        private TestableNodeServiceImpl() {
            ReflectionTestUtils.setField(this, "viteConfigService", Mockito.mock(ViteConfigService.class));
        }

        @Override
        public Node getById(java.io.Serializable id) {
            return node;
        }

        @Override
        public boolean save(Node entity) {
            this.savedNode = entity;
            this.node = entity;
            return true;
        }

        @Override
        public boolean updateById(Node entity) {
            this.updatedNode = entity;
            return true;
        }
    }
}
