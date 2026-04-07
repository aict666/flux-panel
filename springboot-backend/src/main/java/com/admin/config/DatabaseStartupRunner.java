package com.admin.config;

import com.admin.common.dto.TunnelTopologyConfigDto;
import com.admin.entity.ChainTunnel;
import com.admin.service.support.TunnelTopologySupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class DatabaseStartupRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final SqliteToPostgresMigrationService sqliteToPostgresMigrationService;
    private final ConfigurableApplicationContext applicationContext;
    private final TunnelTopologySupport topologySupport = new TunnelTopologySupport();

    @Value("${app.migration.mode:}")
    private String migrationMode;

    @Value("${app.migration.sqlite-path:}")
    private String sqlitePath;

    @Value("${app.migration.exit-after-run:false}")
    private boolean exitAfterRun;

    public DatabaseStartupRunner(DataSource dataSource,
                                 SqliteToPostgresMigrationService sqliteToPostgresMigrationService,
                                 ConfigurableApplicationContext applicationContext) {
        this.dataSource = dataSource;
        this.sqliteToPostgresMigrationService = sqliteToPostgresMigrationService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if ("sqlite-to-postgres".equalsIgnoreCase(migrationMode)) {
            sqliteToPostgresMigrationService.migrate(sqlitePath);
            backfillTopologyJson();
            if (exitAfterRun) {
                int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                CompletableFuture.runAsync(() -> System.exit(exitCode));
            }
            return;
        }

        backfillTopologyJson();
    }

    private void backfillTopologyJson() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!hasColumn(connection, "tunnel", "topology_json") || !hasTable(connection, "chain_tunnel")) {
                return;
            }

            List<Long> tunnelIds = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id
                    FROM tunnel
                    WHERE topology_json IS NULL OR TRIM(topology_json) = ''
                    """);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tunnelIds.add(resultSet.getLong("id"));
                }
            }

            for (Long tunnelId : tunnelIds) {
                TunnelTopologyConfigDto topology = buildLegacyTopology(connection, tunnelId);
                String topologyJson = topologySupport.serializeTopology(topology);
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE tunnel
                        SET topology_json = ?
                        WHERE id = ?
                        """)) {
                    update.setString(1, topologyJson);
                    update.setLong(2, tunnelId);
                    update.executeUpdate();
                }
            }
        }
    }

    private TunnelTopologyConfigDto buildLegacyTopology(Connection connection, Long tunnelId) throws Exception {
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
                    int port = resultSet.getInt("port");
                    chainTunnel.setPort(resultSet.wasNull() ? null : port);
                    chainTunnel.setStrategy(resultSet.getString("strategy"));
                    int inx = resultSet.getInt("inx");
                    chainTunnel.setInx(resultSet.wasNull() ? null : inx);
                    chainTunnel.setProtocol(resultSet.getString("protocol"));
                    chainTunnels.add(chainTunnel);
                }
            }
        }
        return topologySupport.buildLegacyTopology(chainTunnels);
    }

    private boolean hasTable(Connection connection, String tableName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, tableName, null)) {
            return resultSet.next();
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName)) {
            return resultSet.next();
        }
    }
}
