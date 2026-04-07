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

    private void makeReadOnly(Path path) throws IOException {
        Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ));
    }

    private void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }
}
