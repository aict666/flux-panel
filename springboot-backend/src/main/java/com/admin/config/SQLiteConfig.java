package com.admin.config;

import com.admin.common.dto.TunnelTopologyConfigDto;
import com.admin.entity.ChainTunnel;
import com.admin.service.support.TunnelTopologySupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQLite 数据库配置
 * 启用 WAL (Write-Ahead Logging) 模式以提高并发性能
 * 添加定期 checkpoint 和优雅关闭处理
 */
@Slf4j
@Component
@EnableScheduling
public class SQLiteConfig implements ApplicationRunner {

    private final DataSource dataSource;
    private final TunnelTopologySupport topologySupport = new TunnelTopologySupport();

    public SQLiteConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL;");
            statement.execute("PRAGMA synchronous=NORMAL;");
            statement.execute("PRAGMA cache_size=-64000;"); // 64MB 缓存
            statement.execute("PRAGMA temp_store=MEMORY;");
            statement.execute("PRAGMA busy_timeout=5000;"); // 5秒超时
            statement.execute("PRAGMA wal_autocheckpoint=1000;"); // 每1000页自动checkpoint
            ensureTopologyJsonColumn(connection);
            backfillTopologyJson(connection);
            
            log.info("SQLite WAL mode configured successfully");
        } catch (Exception e) {
            log.error("Failed to configure SQLite database", e);
            throw e;
        }
    }
    
    /**
     * 定期执行 checkpoint，确保 WAL 文件内容写入主数据库
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 300000)
    public void performCheckpoint() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            log.debug("SQLite WAL checkpoint completed");
        } catch (Exception e) {
            log.error("Failed to perform SQLite checkpoint", e);
        }
    }
    
    /**
     * 应用关闭前执行最终的 checkpoint，确保所有数据都写入主数据库文件
     */
    @PreDestroy
    public void onShutdown() {
        log.info("Performing final SQLite checkpoint before shutdown...");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // 强制执行 checkpoint，将所有 WAL 内容写入主数据库
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            log.info("Final SQLite checkpoint completed successfully");
        } catch (Exception e) {
            log.error("Failed to perform final SQLite checkpoint", e);
        }
    }

    private void ensureTopologyJsonColumn(Connection connection) throws Exception {
        if (hasColumn(connection, "tunnel", "topology_json")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE tunnel ADD COLUMN topology_json TEXT;");
        }
        log.info("Added topology_json column to tunnel table");
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ");")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void backfillTopologyJson(Connection connection) throws Exception {
        if (!hasColumn(connection, "chain_tunnel", "tunnel_id")) {
            return;
        }

        List<Long> tunnelIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM tunnel WHERE topology_json IS NULL OR TRIM(topology_json) = ''"
        ); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tunnelIds.add(resultSet.getLong("id"));
            }
        }

        for (Long tunnelId : tunnelIds) {
            TunnelTopologyConfigDto topology = buildLegacyTopology(connection, tunnelId);
            if (topology.getInNodeId().isEmpty() && topology.getChainNodes().isEmpty() && topology.getOutNodeId().isEmpty()) {
                continue;
            }
            try (PreparedStatement updateStatement = connection.prepareStatement(
                    "UPDATE tunnel SET topology_json = ? WHERE id = ?"
            )) {
                updateStatement.setString(1, topologySupport.serializeTopology(topology));
                updateStatement.setLong(2, tunnelId);
                updateStatement.executeUpdate();
            }
        }
    }

    private TunnelTopologyConfigDto buildLegacyTopology(Connection connection, Long tunnelId) throws Exception {
        List<ChainTunnel> chainTunnels = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, tunnel_id, chain_type, node_id, port, strategy, inx, protocol " +
                        "FROM chain_tunnel WHERE tunnel_id = ? ORDER BY chain_type, inx, id"
        )) {
            statement.setLong(1, tunnelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ChainTunnel chainTunnel = new ChainTunnel();
                    chainTunnel.setId(resultSet.getLong("id"));
                    chainTunnel.setTunnelId(resultSet.getLong("tunnel_id"));
                    chainTunnel.setChainType(resultSet.getInt("chain_type"));
                    chainTunnel.setNodeId(resultSet.getLong("node_id"));
                    chainTunnel.setPort(resultSet.getObject("port") == null ? null : resultSet.getInt("port"));
                    chainTunnel.setStrategy(resultSet.getString("strategy"));
                    chainTunnel.setInx(resultSet.getObject("inx") == null ? null : resultSet.getInt("inx"));
                    chainTunnel.setProtocol(resultSet.getString("protocol"));
                    chainTunnels.add(chainTunnel);
                }
            }
        }

        TunnelTopologyConfigDto topology = topologySupport.buildLegacyTopology(chainTunnels);
        topology.setChainNodes(topology.getChainNodes().stream()
                .sorted(Comparator.comparing(group -> group.isEmpty() ? 0 : group.getFirst().getHopIndex() == null ? 0 : group.getFirst().getHopIndex()))
                .collect(Collectors.toList()));
        return topology;
    }
}
