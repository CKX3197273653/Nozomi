package org.mate.mate10;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class FlywayMigrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withUsername("mate10db")
            .withImagePullPolicy(imageName -> false);

    @Test
    @DisplayName("Flyway 迁移能在mysql上执行,建出核心表")
    void flywayMigratesCleanly() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .load();
        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isPositive();

        try (Connection conn = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             ResultSet rs = conn.getMetaData().getTables(null, null, "sys_user", null)) {
            assertThat(rs.next()).isTrue();
        }
    }
}
