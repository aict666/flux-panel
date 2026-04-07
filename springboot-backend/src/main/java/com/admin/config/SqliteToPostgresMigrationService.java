package com.admin.config;

import com.admin.common.dto.TunnelTopologyConfigDto;
import com.admin.entity.ChainTunnel;
import com.admin.service.support.TunnelTopologySupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SqliteToPostgresMigrationService {

    private final DataSource dataSource;
    private final TunnelTopologySupport topologySupport = new TunnelTopologySupport();

    public SqliteToPostgresMigrationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate(String sqlitePath) {
        if (sqlitePath == null || sqlitePath.trim().isEmpty()) {
            log.info("未提供 SQLite 数据文件路径，跳过迁移");
            return;
        }

        Path sourcePath = Paths.get(sqlitePath);
        if (!Files.exists(sourcePath)) {
            log.info("SQLite 数据文件不存在，跳过迁移: {}", sqlitePath);
            return;
        }

        try (Connection sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + sourcePath);
             Connection postgresConnection = dataSource.getConnection()) {
            postgresConnection.setAutoCommit(false);
            try {
                if (targetHasExistingData(postgresConnection)) {
                    log.info("目标 PostgreSQL 已存在业务数据，跳过 SQLite 导入");
                    return;
                }
                migrateUsers(sqliteConnection, postgresConnection);
                migrateViteConfigs(sqliteConnection, postgresConnection);
                migrateNodes(sqliteConnection, postgresConnection);
                migrateTunnels(sqliteConnection, postgresConnection);
                migrateChainTunnels(sqliteConnection, postgresConnection);
                migrateForwards(sqliteConnection, postgresConnection);
                migrateForwardPorts(sqliteConnection, postgresConnection);
                migrateSpeedLimits(sqliteConnection, postgresConnection);
                migrateUserTunnels(sqliteConnection, postgresConnection);
                migrateStatisticsFlows(sqliteConnection, postgresConnection);
                resetSequences(postgresConnection);
                postgresConnection.commit();
            } catch (Exception ex) {
                postgresConnection.rollback();
                throw new IllegalStateException("SQLite 到 PostgreSQL 迁移失败", ex);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("无法执行 SQLite 到 PostgreSQL 迁移", ex);
        }
    }

    private boolean targetHasExistingData(Connection postgresConnection) throws SQLException {
        try (Statement statement = postgresConnection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT EXISTS (SELECT 1 FROM "user" LIMIT 1)
                         OR EXISTS (SELECT 1 FROM node LIMIT 1)
                         OR EXISTS (SELECT 1 FROM tunnel LIMIT 1)
                         OR EXISTS (SELECT 1 FROM forward LIMIT 1)
                         OR EXISTS (SELECT 1 FROM statistics_flow LIMIT 1) AS has_data
                     """)) {
            return resultSet.next() && resultSet.getBoolean("has_data");
        }
    }

    private void migrateUsers(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "user")) {
            return;
        }
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, user, pwd, role_id, exp_time, flow, in_flow, out_flow, flow_reset_time, num, created_time, updated_time, status
                FROM user
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO "user" (id, user, pwd, role_id, exp_time, flow, in_flow, out_flow, flow_reset_time, num, created_time, updated_time, status)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                insert.setLong(1, resultSet.getLong("id"));
                insert.setString(2, resultSet.getString("user"));
                insert.setString(3, resultSet.getString("pwd"));
                insert.setInt(4, resultSet.getInt("role_id"));
                insert.setLong(5, resultSet.getLong("exp_time"));
                insert.setLong(6, resultSet.getLong("flow"));
                insert.setLong(7, resultSet.getLong("in_flow"));
                insert.setLong(8, resultSet.getLong("out_flow"));
                insert.setLong(9, resultSet.getLong("flow_reset_time"));
                insert.setInt(10, resultSet.getInt("num"));
                insert.setLong(11, resultSet.getLong("created_time"));
                insert.setObject(12, getNullableLong(resultSet, "updated_time"));
                insert.setInt(13, resultSet.getInt("status"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateViteConfigs(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "vite_config")) {
            return;
        }
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, name, value, time
                FROM vite_config
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO vite_config (id, name, value, time)
                     VALUES (?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                insert.setLong(1, resultSet.getLong("id"));
                insert.setString(2, resultSet.getString("name"));
                insert.setString(3, resultSet.getString("value"));
                insert.setLong(4, resultSet.getLong("time"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateNodes(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "node")) {
            return;
        }
        boolean hasInstallServiceName = hasColumn(sqliteConnection, "node", "install_service_name");
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, name, secret, server_ip, port, interface_name, version, http, tls, socks, created_time, updated_time, status,
                       tcp_listen_addr, udp_listen_addr
                FROM node
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO node (id, name, secret, server_ip, port, install_service_name, interface_name, version, http, tls, socks,
                                       created_time, updated_time, status, tcp_listen_addr, udp_listen_addr)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                insert.setLong(1, resultSet.getLong("id"));
                insert.setString(2, resultSet.getString("name"));
                insert.setString(3, resultSet.getString("secret"));
                insert.setString(4, resultSet.getString("server_ip"));
                insert.setString(5, resultSet.getString("port"));
                insert.setString(6, hasInstallServiceName ? resultSet.getString("install_service_name") : null);
                insert.setString(7, resultSet.getString("interface_name"));
                insert.setString(8, resultSet.getString("version"));
                insert.setInt(9, resultSet.getInt("http"));
                insert.setInt(10, resultSet.getInt("tls"));
                insert.setInt(11, resultSet.getInt("socks"));
                insert.setLong(12, resultSet.getLong("created_time"));
                insert.setObject(13, getNullableLong(resultSet, "updated_time"));
                insert.setInt(14, resultSet.getInt("status"));
                insert.setString(15, resultSet.getString("tcp_listen_addr"));
                insert.setString(16, resultSet.getString("udp_listen_addr"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateTunnels(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "tunnel")) {
            return;
        }
        boolean hasTopologyJson = hasColumn(sqliteConnection, "tunnel", "topology_json");
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, name, traffic_ratio, type, protocol, flow, created_time, updated_time, status, in_ip
                FROM tunnel
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO tunnel (id, name, traffic_ratio, type, protocol, flow, created_time, updated_time, status, in_ip, topology_json)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                long tunnelId = resultSet.getLong("id");
                String topologyJson = hasTopologyJson ? readOptionalText(sqliteConnection, "tunnel", "topology_json", tunnelId) : null;
                if (topologyJson == null || topologyJson.trim().isEmpty()) {
                    TunnelTopologyConfigDto topology = buildLegacyTopology(sqliteConnection, tunnelId);
                    topologyJson = topologySupport.serializeTopology(topology);
                }
                insert.setLong(1, tunnelId);
                insert.setString(2, resultSet.getString("name"));
                insert.setBigDecimal(3, resultSet.getBigDecimal("traffic_ratio"));
                insert.setInt(4, resultSet.getInt("type"));
                insert.setString(5, resultSet.getString("protocol"));
                insert.setInt(6, resultSet.getInt("flow"));
                insert.setLong(7, resultSet.getLong("created_time"));
                insert.setLong(8, resultSet.getLong("updated_time"));
                insert.setInt(9, resultSet.getInt("status"));
                insert.setString(10, resultSet.getString("in_ip"));
                insert.setString(11, topologyJson);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateChainTunnels(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "chain_tunnel")) {
            return;
        }
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, tunnel_id, chain_type, node_id, port, strategy, inx, protocol
                FROM chain_tunnel
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO chain_tunnel (id, tunnel_id, chain_type, node_id, port, strategy, inx, protocol)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                insert.setLong(1, resultSet.getLong("id"));
                insert.setLong(2, resultSet.getLong("tunnel_id"));
                insert.setInt(3, resultSet.getInt("chain_type"));
                insert.setLong(4, resultSet.getLong("node_id"));
                insert.setObject(5, getNullableInteger(resultSet, "port"));
                insert.setString(6, resultSet.getString("strategy"));
                insert.setObject(7, getNullableInteger(resultSet, "inx"));
                insert.setString(8, resultSet.getString("protocol"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateForwards(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "forward")) {
            return;
        }
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, user_id, user_name, name, tunnel_id, remote_addr, strategy, in_flow, out_flow, created_time, updated_time, status, inx
                FROM forward
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO forward (id, user_id, user_name, name, tunnel_id, remote_addr, strategy, in_flow, out_flow, created_time, updated_time, status, inx)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                insert.setLong(1, resultSet.getLong("id"));
                insert.setLong(2, resultSet.getLong("user_id"));
                insert.setString(3, resultSet.getString("user_name"));
                insert.setString(4, resultSet.getString("name"));
                insert.setLong(5, resultSet.getLong("tunnel_id"));
                insert.setString(6, resultSet.getString("remote_addr"));
                insert.setString(7, resultSet.getString("strategy"));
                insert.setLong(8, resultSet.getLong("in_flow"));
                insert.setLong(9, resultSet.getLong("out_flow"));
                insert.setLong(10, resultSet.getLong("created_time"));
                insert.setLong(11, resultSet.getLong("updated_time"));
                insert.setInt(12, resultSet.getInt("status"));
                insert.setInt(13, resultSet.getInt("inx"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateForwardPorts(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "forward_port")) {
            return;
        }
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, forward_id, node_id, port
                FROM forward_port
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO forward_port (id, forward_id, node_id, port)
                     VALUES (?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                insert.setLong(1, resultSet.getLong("id"));
                insert.setLong(2, resultSet.getLong("forward_id"));
                insert.setLong(3, resultSet.getLong("node_id"));
                insert.setInt(4, resultSet.getInt("port"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateSpeedLimits(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "speed_limit")) {
            return;
        }
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, name, speed, tunnel_id, tunnel_name, created_time, updated_time, status
                FROM speed_limit
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO speed_limit (id, name, speed, tunnel_id, tunnel_name, created_time, updated_time, status)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                insert.setLong(1, resultSet.getLong("id"));
                insert.setString(2, resultSet.getString("name"));
                insert.setInt(3, resultSet.getInt("speed"));
                insert.setLong(4, resultSet.getLong("tunnel_id"));
                insert.setString(5, resultSet.getString("tunnel_name"));
                insert.setLong(6, resultSet.getLong("created_time"));
                insert.setObject(7, getNullableLong(resultSet, "updated_time"));
                insert.setInt(8, resultSet.getInt("status"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateUserTunnels(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "user_tunnel")) {
            return;
        }
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, user_id, tunnel_id, speed_id, num, flow, in_flow, out_flow, flow_reset_time, exp_time, status
                FROM user_tunnel
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO user_tunnel (id, user_id, tunnel_id, speed_id, num, flow, in_flow, out_flow, flow_reset_time, exp_time, status)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                insert.setLong(1, resultSet.getLong("id"));
                insert.setLong(2, resultSet.getLong("user_id"));
                insert.setLong(3, resultSet.getLong("tunnel_id"));
                insert.setObject(4, getNullableLong(resultSet, "speed_id"));
                insert.setInt(5, resultSet.getInt("num"));
                insert.setLong(6, resultSet.getLong("flow"));
                insert.setLong(7, resultSet.getLong("in_flow"));
                insert.setLong(8, resultSet.getLong("out_flow"));
                insert.setLong(9, resultSet.getLong("flow_reset_time"));
                insert.setLong(10, resultSet.getLong("exp_time"));
                insert.setInt(11, resultSet.getInt("status"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void migrateStatisticsFlows(Connection sqliteConnection, Connection postgresConnection) throws SQLException {
        if (!hasTable(sqliteConnection, "statistics_flow")) {
            return;
        }
        try (PreparedStatement query = sqliteConnection.prepareStatement("""
                SELECT id, user_id, flow, total_flow, time, created_time
                FROM statistics_flow
                ORDER BY id
                """);
             ResultSet resultSet = query.executeQuery();
             PreparedStatement insert = postgresConnection.prepareStatement("""
                     INSERT INTO statistics_flow (id, user_id, in_flow, out_flow, flow, total_in_flow, total_out_flow, total_flow, hour_time, time, created_time)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO NOTHING
                     """)) {
            while (resultSet.next()) {
                long createdTime = resultSet.getLong("created_time");
                long hourTime = Instant.ofEpochMilli(createdTime)
                        .atZone(ZoneId.systemDefault())
                        .truncatedTo(ChronoUnit.HOURS)
                        .toInstant()
                        .toEpochMilli();
                insert.setLong(1, resultSet.getLong("id"));
                insert.setLong(2, resultSet.getLong("user_id"));
                insert.setLong(3, 0L);
                insert.setLong(4, 0L);
                insert.setLong(5, resultSet.getLong("flow"));
                insert.setLong(6, 0L);
                insert.setLong(7, 0L);
                insert.setLong(8, resultSet.getLong("total_flow"));
                insert.setLong(9, hourTime);
                insert.setString(10, resultSet.getString("time"));
                insert.setLong(11, createdTime);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private TunnelTopologyConfigDto buildLegacyTopology(Connection connection, Long tunnelId) throws SQLException {
        if (!hasTable(connection, "chain_tunnel")) {
            return new TunnelTopologyConfigDto();
        }
        List<ChainTunnel> chainTunnels = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, tunnel_id, chain_type, node_id, port, strategy, inx, protocol
                FROM chain_tunnel
                WHERE tunnel_id = ?
                ORDER BY chain_type, inx, id
                """)) {
            statement.setLong(1, tunnelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ChainTunnel chainTunnel = new ChainTunnel();
                    chainTunnel.setId(resultSet.getLong("id"));
                    chainTunnel.setTunnelId(resultSet.getLong("tunnel_id"));
                    chainTunnel.setChainType(resultSet.getInt("chain_type"));
                    chainTunnel.setNodeId(resultSet.getLong("node_id"));
                    chainTunnel.setPort(getNullableInteger(resultSet, "port"));
                    chainTunnel.setStrategy(resultSet.getString("strategy"));
                    chainTunnel.setInx(getNullableInteger(resultSet, "inx"));
                    chainTunnel.setProtocol(resultSet.getString("protocol"));
                    chainTunnels.add(chainTunnel);
                }
            }
        }
        return topologySupport.buildLegacyTopology(chainTunnels);
    }

    private void resetSequences(Connection postgresConnection) throws SQLException {
        resetSequence(postgresConnection, "\"user\"", "public.\"user\"");
        resetSequence(postgresConnection, "vite_config", "public.vite_config");
        resetSequence(postgresConnection, "node", "public.node");
        resetSequence(postgresConnection, "tunnel", "public.tunnel");
        resetSequence(postgresConnection, "chain_tunnel", "public.chain_tunnel");
        resetSequence(postgresConnection, "forward", "public.forward");
        resetSequence(postgresConnection, "forward_port", "public.forward_port");
        resetSequence(postgresConnection, "speed_limit", "public.speed_limit");
        resetSequence(postgresConnection, "user_tunnel", "public.user_tunnel");
        resetSequence(postgresConnection, "statistics_flow", "public.statistics_flow");
        resetSequence(postgresConnection, "forward_statistics_flow", "public.forward_statistics_flow");
    }

    private void resetSequence(Connection connection, String tableName, String serialTableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    SELECT setval(
                        pg_get_serial_sequence('%s', 'id'),
                        COALESCE((SELECT MAX(id) FROM %s), 1),
                        (SELECT COUNT(*) > 0 FROM %s)
                    )
                    """.formatted(serialTableName, tableName, tableName));
        }
    }

    private boolean hasTable(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, tableName, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(null, null, tableName.toUpperCase(), null)) {
            return resultSet.next();
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            return resultSet.next();
        }
    }

    private String readOptionalText(Connection connection, String tableName, String columnName, long id) throws SQLException {
        String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString(1);
                }
            }
        }
        return null;
    }

    private Long getNullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private Integer getNullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
