CREATE TABLE IF NOT EXISTS `decision_artifact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '归档资产ID',
  `kind` VARCHAR(32) DEFAULT NULL COMMENT '旧资产类型',
  `code` VARCHAR(100) NOT NULL COMMENT '旧资产编码',
  `name` VARCHAR(120) NOT NULL COMMENT '旧资产名称',
  `category` VARCHAR(80) DEFAULT NULL COMMENT '业务分类',
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿' COMMENT '资产状态',
  `tags` VARCHAR(255) DEFAULT NULL COMMENT '标签',
  `owner` VARCHAR(80) DEFAULT NULL COMMENT '负责人',
  `version_no` INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
  `content_json` LONGTEXT NOT NULL COMMENT '旧资产配置JSON',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_artifact_kind_code` (`kind`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策引擎旧资产归档';

CREATE TABLE IF NOT EXISTS `decision_scene` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `category` VARCHAR(80) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `input_schema_json` LONGTEXT,
  `output_schema_json` LONGTEXT,
  `owner` VARCHAR(80) DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_scene_code` (`code`),
  KEY `idx_decision_scene_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策场景';

CREATE TABLE IF NOT EXISTS `decision_variable` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_code` VARCHAR(100) DEFAULT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `type` VARCHAR(32) DEFAULT NULL,
  `source` VARCHAR(32) DEFAULT NULL,
  `source_config_json` LONGTEXT,
  `default_value_json` LONGTEXT,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_variable_scene_code` (`scene_code`, `code`),
  KEY `idx_decision_variable_scene` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策变量';

CREATE TABLE IF NOT EXISTS `decision_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_code` VARCHAR(100) NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `priority` INT DEFAULT 0,
  `expression_type` VARCHAR(32) DEFAULT NULL,
  `match_mode` VARCHAR(32) DEFAULT NULL,
  `required_match` INT DEFAULT 0,
  `condition_expression` LONGTEXT,
  `conditions_json` LONGTEXT,
  `actions_json` LONGTEXT,
  `fallback_action_json` LONGTEXT,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `version_no` INT NOT NULL DEFAULT 1,
  `owner` VARCHAR(80) DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_rule_code` (`code`),
  KEY `idx_decision_rule_scene_status` (`scene_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策规则';

CREATE TABLE IF NOT EXISTS `decision_rule_condition` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `rule_id` BIGINT DEFAULT NULL,
  `condition_key` VARCHAR(100) DEFAULT NULL,
  `field` VARCHAR(100) DEFAULT NULL,
  `operator` VARCHAR(32) DEFAULT NULL,
  `value_json` LONGTEXT,
  `expression` LONGTEXT,
  `matched` TINYINT(1) DEFAULT NULL,
  `sort` INT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_rule_condition_rule` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则条件明细';

CREATE TABLE IF NOT EXISTS `decision_rule_action` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `rule_id` BIGINT DEFAULT NULL,
  `action_type` VARCHAR(32) DEFAULT NULL,
  `action_json` LONGTEXT,
  `sort` INT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_rule_action_rule` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则动作明细';

CREATE TABLE IF NOT EXISTS `decision_rule_set` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_code` VARCHAR(100) NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `strategy` VARCHAR(32) DEFAULT NULL,
  `required_match` INT DEFAULT 0,
  `short_circuit` TINYINT(1) DEFAULT 0,
  `rule_codes_json` LONGTEXT,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `version_no` INT NOT NULL DEFAULT 1,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_rule_set_code` (`code`),
  KEY `idx_decision_rule_set_scene_status` (`scene_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策规则集';

CREATE TABLE IF NOT EXISTS `decision_flow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_code` VARCHAR(100) NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `version_no` INT NOT NULL DEFAULT 1,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_flow_code` (`code`),
  KEY `idx_decision_flow_scene_status` (`scene_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策流';

CREATE TABLE IF NOT EXISTS `decision_flow_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `flow_id` BIGINT NOT NULL,
  `node_key` VARCHAR(100) NOT NULL,
  `type` VARCHAR(32) NOT NULL,
  `code` VARCHAR(100) DEFAULT NULL,
  `label` VARCHAR(120) DEFAULT NULL,
  `enabled` TINYINT(1) DEFAULT 1,
  `sort` INT DEFAULT 0,
  `config_json` LONGTEXT,
  `x` INT DEFAULT 0,
  `y` INT DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_flow_node_flow` (`flow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策流节点';

CREATE TABLE IF NOT EXISTS `decision_flow_edge` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `flow_id` BIGINT NOT NULL,
  `edge_key` VARCHAR(100) DEFAULT NULL,
  `source_key` VARCHAR(100) NOT NULL,
  `target_key` VARCHAR(100) NOT NULL,
  `branch` VARCHAR(32) DEFAULT NULL,
  `label` VARCHAR(120) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_flow_edge_flow` (`flow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策流边';

CREATE TABLE IF NOT EXISTS `decision_data_source` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `type` VARCHAR(32) DEFAULT NULL,
  `config_json` LONGTEXT,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_data_source_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策数据源';

CREATE TABLE IF NOT EXISTS `decision_model_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `provider` VARCHAR(80) DEFAULT NULL,
  `config_json` LONGTEXT,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_model_config_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策模型配置';

CREATE TABLE IF NOT EXISTS `decision_score_card` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_code` VARCHAR(100) DEFAULT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `items_json` LONGTEXT,
  `mapping_json` LONGTEXT,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `version_no` INT NOT NULL DEFAULT 1,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_score_card_code` (`code`),
  KEY `idx_decision_score_card_scene` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策评分卡';

CREATE TABLE IF NOT EXISTS `decision_table` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_code` VARCHAR(100) DEFAULT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `hit_policy` VARCHAR(32) DEFAULT NULL,
  `rows_json` LONGTEXT,
  `status` VARCHAR(32) NOT NULL DEFAULT '草稿',
  `version_no` INT NOT NULL DEFAULT 1,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_table_code` (`code`),
  KEY `idx_decision_table_scene` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策表';

CREATE TABLE IF NOT EXISTS `decision_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `artifact_id` BIGINT DEFAULT NULL COMMENT '旧资产ID',
  `target_type` VARCHAR(32) DEFAULT NULL,
  `target_id` BIGINT DEFAULT NULL,
  `kind` VARCHAR(32) DEFAULT NULL COMMENT '旧资产类型',
  `code` VARCHAR(100) NOT NULL,
  `version_no` INT NOT NULL,
  `snapshot_json` LONGTEXT NOT NULL,
  `remark` VARCHAR(300) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_version_target` (`target_type`, `target_id`, `version_no`),
  KEY `idx_decision_version_legacy` (`artifact_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策版本';

CREATE TABLE IF NOT EXISTS `decision_publish_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `artifact_id` BIGINT DEFAULT NULL COMMENT '旧资产ID',
  `target_type` VARCHAR(32) DEFAULT NULL,
  `target_id` BIGINT DEFAULT NULL,
  `kind` VARCHAR(32) DEFAULT NULL COMMENT '旧资产类型',
  `code` VARCHAR(100) NOT NULL,
  `version_no` INT NOT NULL,
  `environment` VARCHAR(32) NOT NULL DEFAULT 'PROD',
  `status` VARCHAR(32) NOT NULL DEFAULT '已发布',
  `publish_by` VARCHAR(200) DEFAULT NULL,
  `remark` VARCHAR(300) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_publish_record_target` (`target_type`, `target_id`),
  KEY `idx_decision_publish_record_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策发布记录';

CREATE TABLE IF NOT EXISTS `decision_publish_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_type` VARCHAR(32) NOT NULL,
  `target_id` BIGINT DEFAULT NULL,
  `code` VARCHAR(100) DEFAULT NULL,
  `version_no` INT DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `workflow_business_key` VARCHAR(120) DEFAULT NULL,
  `process_instance_id` VARCHAR(120) DEFAULT NULL,
  `workflow_model_id` BIGINT DEFAULT NULL,
  `applicant` VARCHAR(80) DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_publish_request_target` (`target_type`, `target_id`),
  KEY `idx_decision_publish_request_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策发布申请';

CREATE TABLE IF NOT EXISTS `decision_gray_policy` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_code` VARCHAR(100) DEFAULT NULL,
  `target_type` VARCHAR(32) DEFAULT NULL,
  `target_code` VARCHAR(100) DEFAULT NULL,
  `version_no` INT DEFAULT NULL,
  `percent` INT DEFAULT 0,
  `condition_json` LONGTEXT,
  `enabled` TINYINT(1) DEFAULT 0,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_gray_policy_target` (`scene_code`, `target_type`, `target_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策灰度策略';

CREATE TABLE IF NOT EXISTS `decision_execute_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `trace_id` VARCHAR(80) NOT NULL,
  `event_id` VARCHAR(100) DEFAULT NULL,
  `scene_code` VARCHAR(100) NOT NULL,
  `decision_result` VARCHAR(32) DEFAULT NULL,
  `risk_level` VARCHAR(32) DEFAULT NULL,
  `score` INT DEFAULT NULL,
  `request_json` LONGTEXT,
  `response_json` LONGTEXT,
  `hit_rules_json` LONGTEXT,
  `path_json` LONGTEXT,
  `success` TINYINT(1) NOT NULL DEFAULT 1,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `elapsed_ms` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_execute_log_trace` (`trace_id`),
  KEY `idx_decision_execute_log_scene_time` (`scene_code`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策执行日志';

CREATE TABLE IF NOT EXISTS `decision_hit_detail_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `trace_id` VARCHAR(80) NOT NULL,
  `scene_code` VARCHAR(100) DEFAULT NULL,
  `target_type` VARCHAR(32) DEFAULT NULL,
  `target_code` VARCHAR(100) DEFAULT NULL,
  `detail_type` VARCHAR(32) DEFAULT NULL,
  `expression` LONGTEXT,
  `matched` TINYINT(1) DEFAULT NULL,
  `detail_json` LONGTEXT,
  `elapsed_ms` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_hit_detail_trace` (`trace_id`),
  KEY `idx_decision_hit_detail_target` (`target_type`, `target_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策命中明细日志';

CREATE TABLE IF NOT EXISTS `decision_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `operator` VARCHAR(80) DEFAULT NULL,
  `action` VARCHAR(64) NOT NULL,
  `target_kind` VARCHAR(32) DEFAULT NULL,
  `target_code` VARCHAR(100) DEFAULT NULL,
  `detail_json` LONGTEXT,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_audit_log_target` (`target_kind`, `target_code`),
  KEY `idx_decision_audit_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策审计日志';

CREATE TABLE IF NOT EXISTS `decision_simulation_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_code` VARCHAR(100) DEFAULT NULL,
  `name` VARCHAR(120) DEFAULT NULL,
  `status` VARCHAR(32) DEFAULT 'DRAFT',
  `sample_json` LONGTEXT,
  `result_json` LONGTEXT,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_decision_simulation_job_scene` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策仿真任务';
