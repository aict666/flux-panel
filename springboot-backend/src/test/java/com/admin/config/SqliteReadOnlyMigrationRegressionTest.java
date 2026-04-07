package com.admin.config;

import org.junit.jupiter.api.Test;

import java.nio.file.FileSystems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteReadOnlyMigrationRegressionTest {

    @Test
    void migrationSourceShouldUseImmutableReadOnlyJdbcUriForSqliteImports() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/admin/config/SqliteToPostgresMigrationService.java"));

        assertTrue(source.contains("mode=ro&immutable=1"));
        assertFalse(source.contains("DriverManager.getConnection(\"jdbc:sqlite:\" + sourcePath)"));
    }

    @Test
    void immutableReadOnlyJdbcUriShouldReadWalDatabaseFromReadOnlyDirectory() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;
        }

        Path sqliteDir = Files.createTempDirectory("sqlite-readonly");
        Path sqliteFile = sqliteDir.resolve("gost.db");

        try {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("CREATE TABLE user (id INTEGER PRIMARY KEY, user TEXT NOT NULL)");
                statement.execute("INSERT INTO user (id, user) VALUES (1, 'readonly-user')");
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }

            Path walFile = sqliteDir.resolve("gost.db-wal");
            Path shmFile = sqliteDir.resolve("gost.db-shm");
            makeReadOnly(sqliteFile);
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

            try (Connection connection = SqliteToPostgresMigrationService.openReadOnlySqliteConnection(sqliteFile);
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT user FROM user WHERE id = 1")) {
                assertTrue(resultSet.next());
                assertEquals("readonly-user", resultSet.getString("user"));
                assertTrue(SqliteToPostgresMigrationService.buildReadOnlyJdbcUrl(sqliteFile).contains("mode=ro&immutable=1"));
            }
        } finally {
            Files.setPosixFilePermissions(sqliteDir, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ));
            deleteIfExists(sqliteDir.resolve("gost.db-shm"));
            deleteIfExists(sqliteDir.resolve("gost.db-wal"));
            deleteIfExists(sqliteFile);
            deleteIfExists(sqliteDir);
        }
    }

    @Test
    void nodeMigrationSelectShouldAlwaysExposeInstallServiceNameColumn() throws Exception {
        Path sqliteFile = Files.createTempFile("sqlite-node-select", ".db");
        try {
            try (Connection withColumnConnection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
                 Statement statement = withColumnConnection.createStatement()) {
                statement.execute("""
                        CREATE TABLE node (
                          id INTEGER PRIMARY KEY,
                          name TEXT NOT NULL,
                          secret TEXT NOT NULL,
                          server_ip TEXT NOT NULL,
                          port TEXT NOT NULL,
                          install_service_name TEXT,
                          interface_name TEXT,
                          version TEXT,
                          http INTEGER NOT NULL,
                          tls INTEGER NOT NULL,
                          socks INTEGER NOT NULL,
                          created_time INTEGER NOT NULL,
                          updated_time INTEGER,
                          status INTEGER NOT NULL,
                          tcp_listen_addr TEXT NOT NULL,
                          udp_listen_addr TEXT NOT NULL
                        )
                        """);
                statement.execute("""
                        INSERT INTO node (
                          id, name, secret, server_ip, port, install_service_name, interface_name, version, http, tls, socks,
                          created_time, updated_time, status, tcp_listen_addr, udp_listen_addr
                        ) VALUES (
                          1, 'node-a', 'secret-a', '1.1.1.1', '1000', 'flux-node', '', '', 0, 0, 0, 1, NULL, 1, '[::]', '[::]'
                        )
                        """);
            }

            try (Connection withColumnConnection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
                 Statement statement = withColumnConnection.createStatement();
                 ResultSet resultSet = statement.executeQuery(SqliteToPostgresMigrationService.buildNodeMigrationSelectSql(true))) {
                assertTrue(resultSet.next());
                assertEquals("flux-node", resultSet.getString("install_service_name"));
            }

            Files.delete(sqliteFile);
            sqliteFile = Files.createTempFile("sqlite-node-select-old", ".db");

            try (Connection withoutColumnConnection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
                 Statement statement = withoutColumnConnection.createStatement()) {
                statement.execute("""
                        CREATE TABLE node (
                          id INTEGER PRIMARY KEY,
                          name TEXT NOT NULL,
                          secret TEXT NOT NULL,
                          server_ip TEXT NOT NULL,
                          port TEXT NOT NULL,
                          interface_name TEXT,
                          version TEXT,
                          http INTEGER NOT NULL,
                          tls INTEGER NOT NULL,
                          socks INTEGER NOT NULL,
                          created_time INTEGER NOT NULL,
                          updated_time INTEGER,
                          status INTEGER NOT NULL,
                          tcp_listen_addr TEXT NOT NULL,
                          udp_listen_addr TEXT NOT NULL
                        )
                        """);
                statement.execute("""
                        INSERT INTO node (
                          id, name, secret, server_ip, port, interface_name, version, http, tls, socks,
                          created_time, updated_time, status, tcp_listen_addr, udp_listen_addr
                        ) VALUES (
                          1, 'node-b', 'secret-b', '2.2.2.2', '2000', '', '', 0, 0, 0, 1, NULL, 1, '[::]', '[::]'
                        )
                        """);
            }

            try (Connection withoutColumnConnection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
                 Statement statement = withoutColumnConnection.createStatement();
                 ResultSet resultSet = statement.executeQuery(SqliteToPostgresMigrationService.buildNodeMigrationSelectSql(false))) {
                assertTrue(resultSet.next());
                assertEquals(null, resultSet.getString("install_service_name"));
            }
        } finally {
            deleteIfExists(sqliteFile);
        }
    }

    private void makeReadOnly(Path path) throws IOException {
        Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ));
    }

    private void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }
}
