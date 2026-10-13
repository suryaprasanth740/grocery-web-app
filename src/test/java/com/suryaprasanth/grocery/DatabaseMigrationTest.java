package com.suryaprasanth.grocery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tables come from the Flyway migrations, not from Hibernate. These run on H2 and on PostgreSQL in CI,
 * so the same migration file is proven on both. (If a migration and the entities disagree, the app does not
 * start at all, because Hibernate validates the schema: every other test would fail.)
 */
class DatabaseMigrationTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway applied every migration successfully, starting with V1")
    void migrationsApplied() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select version, success from flyway_schema_history where version is not null order by installed_rank");
        assertThat(rows).isNotEmpty();
        assertThat(String.valueOf(rows.get(0).get("version"))).isEqualTo("1");
        assertThat(rows).allSatisfy(r -> assertThat(r.get("success")).isEqualTo(Boolean.TRUE));
    }

    @Test
    @DisplayName("The database itself refuses negative stock and unknown order statuses, even if the Java checks were skipped")
    void databaseConstraints() {
        Long productId = newProduct("10.00", 5).getId();
        assertThatThrownBy(() -> jdbc.update("update products set stock = -1 where id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(stockOf(productRepository.findById(productId).orElseThrow())).isEqualTo(5);

        Long userId = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getId();
        assertThatThrownBy(() -> jdbc.update("insert into orders (user_id, status, payment_method, payment_status, subtotal,"
                + " discount, delivery_fee, gst_included, total_amount, created_at)"
                + " values (?, 'TELEPORTED', 'COD', 'PAID', 1, 0, 0, 0, 1, current_timestamp)", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Two accounts can't share one email: the database has a unique key, not only the Java check")
    void uniqueKeys() {
        assertThatThrownBy(() -> jdbc.update("insert into users (name, email, password_hash, role, created_at)"
                + " values ('Copy', ?, 'x', 'BUYER', current_timestamp)", ADMIN_EMAIL))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
