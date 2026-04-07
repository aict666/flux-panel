package com.admin.config;

import com.admin.entity.User;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresReservedKeywordRegressionTest {

    @Test
    void postgresSchemaShouldUseUsersTableAndUsernameColumn() throws Exception {
        String schema = readClasspathFile("schema.sql");
        String data = readClasspathFile("data.sql");

        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS users ("));
        assertTrue(schema.contains("username VARCHAR(100) NOT NULL"));
        assertFalse(schema.contains("CREATE TABLE IF NOT EXISTS \"user\" ("));
        assertFalse(schema.contains("\n  user VARCHAR(100) NOT NULL"));

        assertTrue(data.contains("INSERT INTO users (id, username, pwd"));
        assertFalse(data.contains("INSERT INTO \"user\" (id, user, pwd"));
    }

    @Test
    void userEntityShouldMapLegacyUserFieldToUsersTableAndUsernameColumn() throws Exception {
        TableName tableName = User.class.getAnnotation(TableName.class);
        assertNotNull(tableName);
        assertEquals("users", tableName.value());

        Field userField = User.class.getDeclaredField("user");
        TableField tableField = userField.getAnnotation(TableField.class);
        assertNotNull(tableField);
        assertEquals("username", tableField.value());
    }

    @Test
    void sourceCodeShouldNotGenerateQueriesAgainstReservedUserIdentifiers() throws IOException {
        String userServiceSource = Files.readString(Path.of("src/main/java/com/admin/service/impl/UserServiceImpl.java"));
        String openApiSource = Files.readString(Path.of("src/main/java/com/admin/controller/OpenApiController.java"));
        String migrationSource = Files.readString(Path.of("src/main/java/com/admin/config/SqliteToPostgresMigrationService.java"));

        assertFalse(userServiceSource.contains("eq(\"user\""));
        assertFalse(openApiSource.contains("eq(\"user\""));
        assertFalse(migrationSource.contains("SELECT EXISTS (SELECT 1 FROM users LIMIT 1)"));
        assertFalse(migrationSource.contains("INSERT INTO \"user\""));
        assertTrue(migrationSource.contains("INSERT INTO users"));
    }

    private String readClasspathFile(String path) throws IOException {
        try (var inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
