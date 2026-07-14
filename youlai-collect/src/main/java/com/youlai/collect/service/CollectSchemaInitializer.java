//package com.youlai.collect.service;
//
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.core.annotation.Order;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//import javax.sql.DataSource;
//import java.sql.Connection;
//import java.util.List;
//import java.util.Locale;
//
//@Component
//@Order(-20)
//public class CollectSchemaInitializer implements ApplicationRunner {
//
//    private final DataSource dataSource;
//    private final JdbcTemplate jdbcTemplate;
//
//    public CollectSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
//        this.dataSource = dataSource;
//        this.jdbcTemplate = jdbcTemplate;
//    }
//
//    @Override
//    public void run(ApplicationArguments args) throws Exception {
//        try (Connection connection = dataSource.getConnection()) {
//            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
//            if (!product.contains("mysql") && !product.contains("mariadb")) {
//                return;
//            }
//            for (String ddl : DDL) {
//                jdbcTemplate.execute(ddl);
//            }
//        }
//    }
//
//    private static final List<String> DDL = List.of(
//            """
//            CREATE TABLE IF NOT EXISTS collect_model (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              model_name VARCHAR(120) NOT NULL,
//              model_code VARCHAR(100) NOT NULL,
//              target_table_name VARCHAR(120) DEFAULT NULL,
//              status VARCHAR(32) NOT NULL DEFAULT 'enabled',
//              field_count INT NOT NULL DEFAULT 0,
//              remark VARCHAR(500) DEFAULT NULL,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              UNIQUE KEY uk_collect_model_code (model_code)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_model_field (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              model_id BIGINT NOT NULL,
//              field_name VARCHAR(120) NOT NULL,
//              field_code VARCHAR(100) NOT NULL,
//              field_type VARCHAR(32) NOT NULL DEFAULT 'string',
//              required_flag TINYINT(1) NOT NULL DEFAULT 0,
//              unique_flag TINYINT(1) NOT NULL DEFAULT 0,
//              default_value VARCHAR(500) DEFAULT NULL,
//              length_limit INT DEFAULT NULL,
//              format_rule VARCHAR(200) DEFAULT NULL,
//              dict_type_code VARCHAR(100) DEFAULT NULL,
//              sort INT NOT NULL DEFAULT 0,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              UNIQUE KEY uk_collect_model_field_code (model_id, field_code)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_api (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              api_name VARCHAR(120) NOT NULL,
//              api_code VARCHAR(100) NOT NULL,
//              collect_type VARCHAR(32) NOT NULL,
//              source_name VARCHAR(120) DEFAULT NULL,
//              timeout_seconds INT DEFAULT 30,
//              max_fetch_count INT DEFAULT 1000,
//              parse_config LONGTEXT,
//              config_json LONGTEXT,
//              status VARCHAR(32) NOT NULL DEFAULT 'enabled',
//              remark VARCHAR(500) DEFAULT NULL,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              UNIQUE KEY uk_collect_api_code (api_code)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_data_source (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              source_name VARCHAR(120) NOT NULL,
//              db_type VARCHAR(32) NOT NULL,
//              jdbc_url VARCHAR(500) NOT NULL,
//              driver_class VARCHAR(200) DEFAULT NULL,
//              username VARCHAR(120) DEFAULT NULL,
//              password_encrypt VARCHAR(500) DEFAULT NULL,
//              connect_timeout INT DEFAULT 10,
//              query_timeout INT DEFAULT 30,
//              pool_config LONGTEXT,
//              pool_min_size INT DEFAULT 1,
//              pool_max_size INT DEFAULT 5,
//              validation_query VARCHAR(200) DEFAULT NULL,
//              last_test_time DATETIME DEFAULT NULL,
//              last_test_status VARCHAR(32) DEFAULT NULL,
//              status VARCHAR(32) NOT NULL DEFAULT 'enabled',
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_task (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              task_name VARCHAR(120) NOT NULL,
//              task_code VARCHAR(100) NOT NULL,
//              model_id BIGINT NOT NULL,
//              api_id BIGINT NOT NULL,
//              schedule_type VARCHAR(32) NOT NULL DEFAULT 'manual',
//              cron_expr VARCHAR(120) DEFAULT NULL,
//              job_id BIGINT DEFAULT NULL,
//              collect_mode VARCHAR(32) NOT NULL DEFAULT 'full',
//              last_success_time DATETIME DEFAULT NULL,
//              last_cursor VARCHAR(500) DEFAULT NULL,
//              insert_strategy VARCHAR(32) NOT NULL DEFAULT 'insert',
//              max_fetch_count INT DEFAULT 1000,
//              mapping_json LONGTEXT,
//              transform_json LONGTEXT,
//              status VARCHAR(32) NOT NULL DEFAULT 'draft',
//              remark VARCHAR(500) DEFAULT NULL,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              UNIQUE KEY uk_collect_task_code (task_code)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_instance (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              task_id BIGINT NOT NULL,
//              job_log_id BIGINT DEFAULT NULL,
//              trace_id VARCHAR(64) NOT NULL,
//              mq_message_id VARCHAR(120) DEFAULT NULL,
//              trigger_type VARCHAR(32) NOT NULL,
//              status VARCHAR(32) NOT NULL,
//              start_time DATETIME DEFAULT NULL,
//              end_time DATETIME DEFAULT NULL,
//              total_count INT DEFAULT 0,
//              valid_count INT DEFAULT 0,
//              invalid_count INT DEFAULT 0,
//              duplicate_count INT DEFAULT 0,
//              inserted_count INT DEFAULT 0,
//              updated_count INT DEFAULT 0,
//              failed_count INT DEFAULT 0,
//              error_message VARCHAR(1000) DEFAULT NULL,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              KEY idx_collect_instance_task_id (task_id),
//              KEY idx_collect_instance_trace_id (trace_id)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_task_message (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              task_id BIGINT NOT NULL,
//              instance_id BIGINT NOT NULL,
//              trace_id VARCHAR(64) NOT NULL,
//              mq_topic VARCHAR(120) DEFAULT NULL,
//              mq_message_id VARCHAR(120) DEFAULT NULL,
//              message_body LONGTEXT,
//              send_status VARCHAR(32) NOT NULL DEFAULT 'pending',
//              consume_status VARCHAR(32) NOT NULL DEFAULT 'pending',
//              send_time DATETIME DEFAULT NULL,
//              consume_time DATETIME DEFAULT NULL,
//              finish_time DATETIME DEFAULT NULL,
//              error_message VARCHAR(1000) DEFAULT NULL,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              KEY idx_collect_message_instance_id (instance_id),
//              KEY idx_collect_message_trace_id (trace_id)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_raw_data (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              task_id BIGINT NOT NULL,
//              instance_id BIGINT NOT NULL,
//              trace_id VARCHAR(64) NOT NULL,
//              data_index INT NOT NULL DEFAULT 0,
//              raw_body LONGTEXT,
//              status VARCHAR(32) NOT NULL DEFAULT 'pending',
//              error_message VARCHAR(1000) DEFAULT NULL,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              KEY idx_collect_raw_instance_id (instance_id),
//              KEY idx_collect_raw_trace_id (trace_id)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_error_data (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              task_id BIGINT NOT NULL,
//              instance_id BIGINT NOT NULL,
//              trace_id VARCHAR(64) NOT NULL,
//              data_index INT NOT NULL DEFAULT 0,
//              error_type VARCHAR(64) NOT NULL,
//              error_message VARCHAR(1000) DEFAULT NULL,
//              raw_body LONGTEXT,
//              transformed_body LONGTEXT,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              KEY idx_collect_error_instance_id (instance_id),
//              KEY idx_collect_error_trace_id (trace_id)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """,
//            """
//            CREATE TABLE IF NOT EXISTS collect_quality_report (
//              id BIGINT NOT NULL AUTO_INCREMENT,
//              task_id BIGINT NOT NULL,
//              instance_id BIGINT NOT NULL,
//              trace_id VARCHAR(64) NOT NULL,
//              total_count INT NOT NULL DEFAULT 0,
//              valid_count INT NOT NULL DEFAULT 0,
//              invalid_count INT NOT NULL DEFAULT 0,
//              duplicate_count INT NOT NULL DEFAULT 0,
//              inserted_count INT NOT NULL DEFAULT 0,
//              updated_count INT NOT NULL DEFAULT 0,
//              failed_count INT NOT NULL DEFAULT 0,
//              field_completeness_json LONGTEXT,
//              summary_json LONGTEXT,
//              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
//              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//              PRIMARY KEY (id),
//              KEY idx_collect_quality_instance_id (instance_id),
//              KEY idx_collect_quality_trace_id (trace_id)
//            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
//            """
//    );
//}
