package com.pharmassist.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test against the real MySQL database configured via DB_URL / DB_USERNAME / DB_PASSWORD.
 */
@SpringBootTest
class DatabaseMigrationTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void connectsToMySql8() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData metaData = connection.getMetaData();
			assertThat(metaData.getDatabaseProductName()).isEqualTo("MySQL");
			assertThat(metaData.getDatabaseMajorVersion()).isEqualTo(8);
		}
	}

	@Test
	void initialMigrationIsApplied() {
		MigrationInfo current = flyway.info().current();
		assertThat(current).isNotNull();
		assertThat(current.getVersion().getVersion()).isEqualTo("1");
		assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
	}

	@Test
	void connectionCheckTableExists() {
		Integer tables = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'connection_check'",
				Integer.class);
		assertThat(tables).isEqualTo(1);
	}

	@Test
	@Transactional
	void connectionCheckTableAcceptsWrites() {
		jdbcTemplate.update("INSERT INTO connection_check (note) VALUES (?)", "integration-test");
		String note = jdbcTemplate.queryForObject(
				"SELECT note FROM connection_check WHERE note = 'integration-test'", String.class);
		assertThat(note).isEqualTo("integration-test");
		// @Transactional rolls the insert back after the test.
	}
}
