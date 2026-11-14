package com.badminton.mes.module.system;

import java.sql.Connection;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.assertThat;

class DevDemoAccountMigrationTest {
    @TempDir Path migrationDirectory;

    @Test
    void migrationsDoNotCreateDemoUsersInAFreshDatabase() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:demo_disabled_fresh;MODE=MySQL", "sa", "")) {
            createSchema(connection);
            replayMigrations(connection);
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_user")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    @Test
    void migrationReplayKeepsDeletedUsersAndBindingsDisabled() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:demo_disabled_existing;MODE=MySQL", "sa", "")) {
            createSchema(connection);
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO sys_user VALUES (2, 'pmc_demo', 0, 1)");
                statement.execute("INSERT INTO rag_tenant_binding (user_id, tenant_id, enabled) VALUES (2, 'user_1', 0)");
            }
            replayMigrations(connection);
            replayMigrations(connection);
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT status, is_deleted FROM sys_user WHERE id=2")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("status")).isZero();
                assertThat(rows.getBoolean("is_deleted")).isTrue();
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT enabled FROM rag_tenant_binding WHERE user_id=2")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean("enabled")).isFalse();
            }
        }
    }

    private void createSchema(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, user_no VARCHAR(64), status INT, is_deleted INT)");
        }
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                "db/migration/V2026110801__create_rag_tenant_binding.sql"));
    }

    private void replayMigrations(Connection connection) throws Exception {
        for (String name : new String[]{"R__ZZZZ_Insert_Demo_Users.sql", "R__Insert_Rag_Tenant_Binding.sql"}) {
            try (var source = new ClassPathResource("db/dev-migration/" + name).getInputStream()) {
                Files.copy(source, migrationDirectory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Flyway.configure()
                .dataSource(connection.getMetaData().getURL(), "sa", "")
                .locations("filesystem:" + migrationDirectory)
                .baselineOnMigrate(true)
                .load().migrate();
    }
}
