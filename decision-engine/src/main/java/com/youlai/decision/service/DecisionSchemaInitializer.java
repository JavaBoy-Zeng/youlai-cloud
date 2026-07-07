package com.youlai.decision.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;

@Component
@Order(-20)
public class DecisionSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DecisionSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (!product.contains("mysql") && !product.contains("mariadb")) {
                return;
            }
            for (String statement : DDL) {
                jdbcTemplate.execute(statement);
            }
            ensureLegacyVersionColumns();
            ensureLegacyPublishColumns();
        }
    }

    private void ensureLegacyVersionColumns() {
        ensureColumn("decision_version", "target_type", "ALTER TABLE decision_version ADD COLUMN target_type VARCHAR(32) NULL AFTER artifact_id");
        ensureColumn("decision_version", "target_id", "ALTER TABLE decision_version ADD COLUMN target_id BIGINT NULL AFTER target_type");
        ignoreFailure("ALTER TABLE decision_version MODIFY artifact_id BIGINT NULL");
        ignoreFailure("ALTER TABLE decision_version MODIFY kind VARCHAR(32) NULL");
    }

    private void ensureLegacyPublishColumns() {
        ensureColumn("decision_publish_record", "target_type", "ALTER TABLE decision_publish_record ADD COLUMN target_type VARCHAR(32) NULL AFTER artifact_id");
        ensureColumn("decision_publish_record", "target_id", "ALTER TABLE decision_publish_record ADD COLUMN target_id BIGINT NULL AFTER target_type");
        ignoreFailure("ALTER TABLE decision_publish_record MODIFY artifact_id BIGINT NULL");
        ignoreFailure("ALTER TABLE decision_publish_record MODIFY kind VARCHAR(32) NULL");
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void ignoreFailure(String ddl) {
        try {
            jdbcTemplate.execute(ddl);
        } catch (Exception ignored) {
            // Older local schemas may already be compatible, and some engines reject no-op MODIFY.
        }
    }

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS decision_scene (
              id BIGINT NOT NULL AUTO_INCREMENT,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              category VARCHAR(80) DEFAULT NULL,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              input_schema_json LONGTEXT,
              output_schema_json LONGTEXT,
              owner VARCHAR(80) DEFAULT NULL,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_scene_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_variable (
              id BIGINT NOT NULL AUTO_INCREMENT,
              scene_code VARCHAR(100) DEFAULT NULL,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              type VARCHAR(32) DEFAULT NULL,
              source VARCHAR(32) DEFAULT NULL,
              source_config_json LONGTEXT,
              default_value_json LONGTEXT,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_variable_scene_code (scene_code, code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_rule (
              id BIGINT NOT NULL AUTO_INCREMENT,
              scene_code VARCHAR(100) NOT NULL,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              priority INT DEFAULT 0,
              expression_type VARCHAR(32) DEFAULT NULL,
              match_mode VARCHAR(32) DEFAULT NULL,
              required_match INT DEFAULT 0,
              condition_expression LONGTEXT,
              conditions_json LONGTEXT,
              actions_json LONGTEXT,
              fallback_action_json LONGTEXT,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              version_no INT NOT NULL DEFAULT 1,
              owner VARCHAR(80) DEFAULT NULL,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_rule_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_rule_condition (
              id BIGINT NOT NULL AUTO_INCREMENT,
              rule_id BIGINT DEFAULT NULL,
              condition_key VARCHAR(100) DEFAULT NULL,
              field VARCHAR(100) DEFAULT NULL,
              operator VARCHAR(32) DEFAULT NULL,
              value_json LONGTEXT,
              expression LONGTEXT,
              matched TINYINT(1) DEFAULT NULL,
              sort INT DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_rule_action (
              id BIGINT NOT NULL AUTO_INCREMENT,
              rule_id BIGINT DEFAULT NULL,
              action_type VARCHAR(32) DEFAULT NULL,
              action_json LONGTEXT,
              sort INT DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_rule_set (
              id BIGINT NOT NULL AUTO_INCREMENT,
              scene_code VARCHAR(100) NOT NULL,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              strategy VARCHAR(32) DEFAULT NULL,
              required_match INT DEFAULT 0,
              short_circuit TINYINT(1) DEFAULT 0,
              rule_codes_json LONGTEXT,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              version_no INT NOT NULL DEFAULT 1,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_rule_set_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_flow (
              id BIGINT NOT NULL AUTO_INCREMENT,
              scene_code VARCHAR(100) NOT NULL,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              version_no INT NOT NULL DEFAULT 1,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_flow_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_flow_node (
              id BIGINT NOT NULL AUTO_INCREMENT,
              flow_id BIGINT NOT NULL,
              node_key VARCHAR(100) NOT NULL,
              type VARCHAR(32) NOT NULL,
              code VARCHAR(100) DEFAULT NULL,
              label VARCHAR(120) DEFAULT NULL,
              enabled TINYINT(1) DEFAULT 1,
              sort INT DEFAULT 0,
              config_json LONGTEXT,
              x INT DEFAULT 0,
              y INT DEFAULT 0,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_flow_edge (
              id BIGINT NOT NULL AUTO_INCREMENT,
              flow_id BIGINT NOT NULL,
              edge_key VARCHAR(100) DEFAULT NULL,
              source_key VARCHAR(100) NOT NULL,
              target_key VARCHAR(100) NOT NULL,
              branch VARCHAR(32) DEFAULT NULL,
              label VARCHAR(120) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_data_source (
              id BIGINT NOT NULL AUTO_INCREMENT,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              type VARCHAR(32) DEFAULT NULL,
              config_json LONGTEXT,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_data_source_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_model_config (
              id BIGINT NOT NULL AUTO_INCREMENT,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              provider VARCHAR(80) DEFAULT NULL,
              config_json LONGTEXT,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_model_config_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_score_card (
              id BIGINT NOT NULL AUTO_INCREMENT,
              scene_code VARCHAR(100) DEFAULT NULL,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              items_json LONGTEXT,
              mapping_json LONGTEXT,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              version_no INT NOT NULL DEFAULT 1,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_score_card_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_table (
              id BIGINT NOT NULL AUTO_INCREMENT,
              scene_code VARCHAR(100) DEFAULT NULL,
              code VARCHAR(100) NOT NULL,
              name VARCHAR(120) NOT NULL,
              hit_policy VARCHAR(32) DEFAULT NULL,
              rows_json LONGTEXT,
              status VARCHAR(32) NOT NULL DEFAULT '草稿',
              version_no INT NOT NULL DEFAULT 1,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uk_decision_table_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_version (
              id BIGINT NOT NULL AUTO_INCREMENT,
              artifact_id BIGINT DEFAULT NULL,
              target_type VARCHAR(32) DEFAULT NULL,
              target_id BIGINT DEFAULT NULL,
              kind VARCHAR(32) DEFAULT NULL,
              code VARCHAR(100) NOT NULL,
              version_no INT NOT NULL,
              snapshot_json LONGTEXT NOT NULL,
              remark VARCHAR(300) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_publish_record (
              id BIGINT NOT NULL AUTO_INCREMENT,
              artifact_id BIGINT DEFAULT NULL,
              target_type VARCHAR(32) DEFAULT NULL,
              target_id BIGINT DEFAULT NULL,
              kind VARCHAR(32) DEFAULT NULL,
              code VARCHAR(100) NOT NULL,
              version_no INT NOT NULL,
              environment VARCHAR(32) NOT NULL DEFAULT 'PROD',
              status VARCHAR(32) NOT NULL DEFAULT '已发布',
              publish_by VARCHAR(200) DEFAULT NULL,
              remark VARCHAR(300) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_publish_request (
              id BIGINT NOT NULL AUTO_INCREMENT,
              target_type VARCHAR(32) NOT NULL,
              target_id BIGINT DEFAULT NULL,
              code VARCHAR(100) DEFAULT NULL,
              version_no INT DEFAULT NULL,
              status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
              workflow_business_key VARCHAR(120) DEFAULT NULL,
              process_instance_id VARCHAR(120) DEFAULT NULL,
              workflow_model_id BIGINT DEFAULT NULL,
              applicant VARCHAR(80) DEFAULT NULL,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_gray_policy (
              id BIGINT NOT NULL AUTO_INCREMENT,
              scene_code VARCHAR(100) DEFAULT NULL,
              target_type VARCHAR(32) DEFAULT NULL,
              target_code VARCHAR(100) DEFAULT NULL,
              version_no INT DEFAULT NULL,
              percent INT DEFAULT 0,
              condition_json LONGTEXT,
              enabled TINYINT(1) DEFAULT 0,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_execute_log (
              id BIGINT NOT NULL AUTO_INCREMENT,
              trace_id VARCHAR(80) NOT NULL,
              event_id VARCHAR(100) DEFAULT NULL,
              scene_code VARCHAR(100) NOT NULL,
              decision_result VARCHAR(32) DEFAULT NULL,
              risk_level VARCHAR(32) DEFAULT NULL,
              score INT DEFAULT NULL,
              request_json LONGTEXT,
              response_json LONGTEXT,
              hit_rules_json LONGTEXT,
              path_json LONGTEXT,
              success TINYINT(1) NOT NULL DEFAULT 1,
              error_message VARCHAR(1000) DEFAULT NULL,
              elapsed_ms BIGINT DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_hit_detail_log (
              id BIGINT NOT NULL AUTO_INCREMENT,
              trace_id VARCHAR(80) NOT NULL,
              scene_code VARCHAR(100) DEFAULT NULL,
              target_type VARCHAR(32) DEFAULT NULL,
              target_code VARCHAR(100) DEFAULT NULL,
              detail_type VARCHAR(32) DEFAULT NULL,
              expression LONGTEXT,
              matched TINYINT(1) DEFAULT NULL,
              detail_json LONGTEXT,
              elapsed_ms BIGINT DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_audit_log (
              id BIGINT NOT NULL AUTO_INCREMENT,
              operator VARCHAR(80) DEFAULT NULL,
              action VARCHAR(64) NOT NULL,
              target_kind VARCHAR(32) DEFAULT NULL,
              target_code VARCHAR(100) DEFAULT NULL,
              detail_json LONGTEXT,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS decision_simulation_job (
              id BIGINT NOT NULL AUTO_INCREMENT,
              scene_code VARCHAR(100) DEFAULT NULL,
              name VARCHAR(120) DEFAULT NULL,
              status VARCHAR(32) DEFAULT 'DRAFT',
              sample_json LONGTEXT,
              result_json LONGTEXT,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );
}
