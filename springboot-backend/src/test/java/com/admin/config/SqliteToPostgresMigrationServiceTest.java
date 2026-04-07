package com.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class SqliteToPostgresMigrationServiceTest {

    @Test
    void shouldMigrateSqliteDataAndRemainIdempotent() throws Exception {
        Path sqliteFile = Files.createTempFile("flux-migration-test", ".db");
        prepareSqliteFixture(sqliteFile);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
            }

            SqliteToPostgresMigrationService migrationService = new SqliteToPostgresMigrationService(dataSource);
            migrationService.migrate(sqliteFile.toString());
            migrationService.migrate(sqliteFile.toString());

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                ResultSet userResult = statement.executeQuery("SELECT username FROM users WHERE id = 1");
                userResult.next();
                assertEquals("admin_user", userResult.getString("username"));

                ResultSet nodeResult = statement.executeQuery("SELECT COUNT(*) FROM node WHERE id = 5");
                nodeResult.next();
                assertEquals(1, nodeResult.getInt(1));

                ResultSet forwardResult = statement.executeQuery("SELECT COUNT(*) FROM forward WHERE id = 11");
                forwardResult.next();
                assertEquals(1, forwardResult.getInt(1));

                ResultSet statsResult = statement.executeQuery("SELECT flow, total_flow FROM statistics_flow WHERE id = 13");
                statsResult.next();
                assertEquals(30, statsResult.getLong("flow"));
                assertEquals(30, statsResult.getLong("total_flow"));
            }
        }
    }

    @Test
    void shouldMigrateIntoEmptyPostgresSchemaWhenTablesAlreadyExist() throws Exception {
        Path sqliteFile = Files.createTempFile("flux-migration-empty-schema", ".db");
        prepareSqliteFixture(sqliteFile);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            }

            SqliteToPostgresMigrationService migrationService = new SqliteToPostgresMigrationService(dataSource);
            migrationService.migrate(sqliteFile.toString());

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                ResultSet userCountResult = statement.executeQuery("SELECT COUNT(*) FROM users WHERE id = 1");
                userCountResult.next();
                assertEquals(1, userCountResult.getInt(1));

                ResultSet statsCountResult = statement.executeQuery("SELECT COUNT(*) FROM statistics_flow WHERE id = 13");
                statsCountResult.next();
                assertEquals(1, statsCountResult.getInt(1));
            }
        }
    }

    @Test
    void shouldMigrateReadOnlyWalSqliteData() throws Exception {
        Path sqliteDir = Files.createTempDirectory("flux-migration-readonly");
        Path sqliteFile = sqliteDir.resolve("gost.db");
        prepareSqliteFixture(sqliteFile);
        setReadOnlyFixture(sqliteDir, sqliteFile);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
            }

            SqliteToPostgresMigrationService migrationService = new SqliteToPostgresMigrationService(dataSource);
            migrationService.migrate(sqliteFile.toString());

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                ResultSet userResult = statement.executeQuery("SELECT username FROM users WHERE id = 1");
                userResult.next();
                assertEquals("admin_user", userResult.getString("username"));

                ResultSet forwardResult = statement.executeQuery("SELECT COUNT(*) FROM forward WHERE id = 11");
                forwardResult.next();
                assertEquals(1, forwardResult.getInt(1));
            }
        } finally {
            resetWritableFixture(sqliteDir, sqliteFile);
            deleteIfExists(sqliteDir.resolve("gost.db-shm"));
            deleteIfExists(sqliteDir.resolve("gost.db-wal"));
            deleteIfExists(sqliteFile);
            deleteIfExists(sqliteDir);
        }
    }

    private void prepareSqliteFixture(Path sqliteFile) throws Exception {
        try (Connection sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile)) {
            try (Statement statement = sqliteConnection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("""
                        CREATE TABLE user (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          user VARCHAR(100) NOT NULL,
                          pwd VARCHAR(100) NOT NULL,
                          role_id INTEGER NOT NULL,
                          exp_time INTEGER NOT NULL,
                          flow INTEGER NOT NULL,
                          in_flow INTEGER NOT NULL DEFAULT 0,
                          out_flow INTEGER NOT NULL DEFAULT 0,
                          flow_reset_time INTEGER NOT NULL,
                          num INTEGER NOT NULL,
                          created_time INTEGER NOT NULL,
                          updated_time INTEGER,
                          status INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE vite_config (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          name VARCHAR(200) NOT NULL UNIQUE,
                          value VARCHAR(200) NOT NULL,
                          time INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE node (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          name VARCHAR(100) NOT NULL,
                          secret VARCHAR(100) NOT NULL,
                          server_ip VARCHAR(100) NOT NULL,
                          port TEXT NOT NULL,
                          interface_name VARCHAR(200),
                          version VARCHAR(100),
                          http INTEGER NOT NULL DEFAULT 0,
                          tls INTEGER NOT NULL DEFAULT 0,
                          socks INTEGER NOT NULL DEFAULT 0,
                          created_time INTEGER NOT NULL,
                          updated_time INTEGER,
                          status INTEGER NOT NULL,
                          tcp_listen_addr VARCHAR(100) NOT NULL DEFAULT '[::]',
                          udp_listen_addr VARCHAR(100) NOT NULL DEFAULT '[::]'
                        )
                        """);
                statement.execute("""
                        CREATE TABLE tunnel (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          name VARCHAR(100) NOT NULL,
                          traffic_ratio REAL NOT NULL DEFAULT 1.0,
                          type INTEGER NOT NULL,
                          protocol VARCHAR(10) NOT NULL DEFAULT 'tls',
                          flow INTEGER NOT NULL,
                          created_time INTEGER NOT NULL,
                          updated_time INTEGER NOT NULL,
                          status INTEGER NOT NULL,
                          in_ip TEXT
                        )
                        """);
                statement.execute("""
                        CREATE TABLE chain_tunnel (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            tunnel_id INTEGER NOT NULL ,
                            chain_type INTEGER NOT NULL,
                            node_id INTEGER NOT NULL ,
                            port INTEGER,
                            strategy VARCHAR(10),
                            inx  INTEGER,
                            protocol  VARCHAR(10)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE forward (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          user_id INTEGER NOT NULL,
                          user_name VARCHAR(100) NOT NULL,
                          name VARCHAR(100) NOT NULL,
                          tunnel_id INTEGER NOT NULL,
                          remote_addr TEXT NOT NULL,
                          strategy VARCHAR(100) NOT NULL DEFAULT 'fifo',
                          in_flow INTEGER NOT NULL DEFAULT 0,
                          out_flow INTEGER NOT NULL DEFAULT 0,
                          created_time INTEGER NOT NULL,
                          updated_time INTEGER NOT NULL,
                          status INTEGER NOT NULL,
                          inx INTEGER NOT NULL DEFAULT 0
                        )
                        """);
                statement.execute("""
                        CREATE TABLE forward_port (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          forward_id INTEGER NOT NULL,
                          node_id INTEGER NOT NULL,
                          port INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE speed_limit (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          name VARCHAR(100) NOT NULL,
                          speed INTEGER NOT NULL,
                          tunnel_id INTEGER NOT NULL,
                          tunnel_name VARCHAR(100) NOT NULL,
                          created_time INTEGER NOT NULL,
                          updated_time INTEGER,
                          status INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE user_tunnel (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          user_id INTEGER NOT NULL,
                          tunnel_id INTEGER NOT NULL,
                          speed_id INTEGER,
                          num INTEGER NOT NULL,
                          flow INTEGER NOT NULL,
                          in_flow INTEGER NOT NULL DEFAULT 0,
                          out_flow INTEGER NOT NULL DEFAULT 0,
                          flow_reset_time INTEGER NOT NULL,
                          exp_time INTEGER NOT NULL,
                          status INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE statistics_flow (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          user_id INTEGER NOT NULL,
                          flow INTEGER NOT NULL,
                          total_flow INTEGER NOT NULL,
                          time VARCHAR(100) NOT NULL,
                          created_time INTEGER NOT NULL
                        )
                        """);
                statement.execute("INSERT INTO user (id, user, pwd, role_id, exp_time, flow, in_flow, out_flow, flow_reset_time, num, created_time, updated_time, status) VALUES (1, 'admin_user', 'pwd', 0, 2727251700000, 99999, 0, 0, 1, 99999, 1748914865000, 1754011744252, 1)");
                statement.execute("INSERT INTO vite_config (id, name, value, time) VALUES (1, 'app_name', 'flux', 1755147963000)");
                statement.execute("INSERT INTO node (id, name, secret, server_ip, port, interface_name, version, http, tls, socks, created_time, updated_time, status, tcp_listen_addr, udp_listen_addr) VALUES (5, 'node-a', 'secret-a', '1.1.1.1', '1000-1001', '', '', 0, 0, 0, 1, 1, 1, '[::]', '[::]')");
                statement.execute("INSERT INTO tunnel (id, name, traffic_ratio, type, protocol, flow, created_time, updated_time, status, in_ip) VALUES (7, 'tunnel-a', 1.0, 2, 'tcp', 1, 1, 1, 1, '1.2.3.4')");
                statement.execute("INSERT INTO chain_tunnel (id, tunnel_id, chain_type, node_id, port, strategy, inx, protocol) VALUES (9, 7, 1, 5, NULL, NULL, NULL, 'tcp')");
                statement.execute("INSERT INTO forward (id, user_id, user_name, name, tunnel_id, remote_addr, strategy, in_flow, out_flow, created_time, updated_time, status, inx) VALUES (11, 1, 'admin_user', 'forward-a', 7, '8.8.8.8:53', 'fifo', 10, 20, 1, 1, 1, 0)");
                statement.execute("INSERT INTO forward_port (id, forward_id, node_id, port) VALUES (12, 11, 5, 18080)");
                statement.execute("INSERT INTO statistics_flow (id, user_id, flow, total_flow, time, created_time) VALUES (13, 1, 30, 30, '10:00', 1710000000000)");
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
    }

    private void setReadOnlyFixture(Path sqliteDir, Path sqliteFile) throws Exception {
        makeReadOnly(sqliteFile);
        Path walFile = sqliteDir.resolve("gost.db-wal");
        Path shmFile = sqliteDir.resolve("gost.db-shm");
        if (Files.exists(walFile)) {
            makeReadOnly(walFile);
        }
        if (Files.exists(shmFile)) {
            makeReadOnly(shmFile);
        }
        Files.setPosixFilePermissions(sqliteDir, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE
        ));
    }

    private void resetWritableFixture(Path sqliteDir, Path sqliteFile) throws Exception {
        if (Files.exists(sqliteDir)) {
            Files.setPosixFilePermissions(sqliteDir, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ));
        }
        if (Files.exists(sqliteFile)) {
            Files.setPosixFilePermissions(sqliteFile, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ));
        }
        Path walFile = sqliteDir.resolve("gost.db-wal");
        Path shmFile = sqliteDir.resolve("gost.db-shm");
        if (Files.exists(walFile)) {
            Files.setPosixFilePermissions(walFile, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ));
        }
        if (Files.exists(shmFile)) {
            Files.setPosixFilePermissions(shmFile, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ));
        }
    }

    private void makeReadOnly(Path path) throws Exception {
        Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ));
    }

    private void deleteIfExists(Path path) throws Exception {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }
}
