DROP TABLE IF EXISTS decision_audit_log;
DROP TABLE IF EXISTS decision_execute_log;
DROP TABLE IF EXISTS decision_publish_record;
DROP TABLE IF EXISTS decision_version;
DROP TABLE IF EXISTS decision_artifact;

CREATE TABLE decision_artifact (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  kind VARCHAR(32),
  code VARCHAR(100) NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(80),
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  tags VARCHAR(255),
  owner VARCHAR(80),
  version_no INT NOT NULL DEFAULT 1,
  content_json CLOB NOT NULL,
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_decision_artifact_kind_code UNIQUE (kind, code)
);

CREATE TABLE decision_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  artifact_id BIGINT,
  target_type VARCHAR(32),
  target_id BIGINT,
  kind VARCHAR(32),
  code VARCHAR(100) NOT NULL,
  version_no INT NOT NULL,
  snapshot_json CLOB NOT NULL,
  remark VARCHAR(300),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_publish_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  artifact_id BIGINT,
  target_type VARCHAR(32),
  target_id BIGINT,
  kind VARCHAR(32) NOT NULL,
  code VARCHAR(100) NOT NULL,
  version_no INT NOT NULL,
  environment VARCHAR(32) NOT NULL DEFAULT 'PROD',
  status VARCHAR(32) NOT NULL DEFAULT '已发布',
  publish_by VARCHAR(200),
  remark VARCHAR(300),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_execute_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  trace_id VARCHAR(80) NOT NULL,
  event_id VARCHAR(100),
  scene_code VARCHAR(100) NOT NULL,
  decision_result VARCHAR(32),
  risk_level VARCHAR(32),
  score INT,
  request_json CLOB,
  response_json CLOB,
  hit_rules_json CLOB,
  path_json CLOB,
  success BOOLEAN NOT NULL DEFAULT TRUE,
  error_message VARCHAR(1000),
  elapsed_ms BIGINT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator VARCHAR(80),
  action VARCHAR(64) NOT NULL,
  target_kind VARCHAR(32),
  target_code VARCHAR(100),
  detail_json CLOB,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_scene (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(80),
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  input_schema_json CLOB,
  output_schema_json CLOB,
  owner VARCHAR(80),
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_variable (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene_code VARCHAR(100),
  code VARCHAR(100) NOT NULL,
  name VARCHAR(120) NOT NULL,
  type VARCHAR(32),
  source VARCHAR(32),
  source_config_json CLOB,
  default_value_json CLOB,
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_decision_variable_scene_code UNIQUE (scene_code, code)
);

CREATE TABLE decision_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene_code VARCHAR(100) NOT NULL,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  priority INT DEFAULT 0,
  expression_type VARCHAR(32),
  match_mode VARCHAR(32),
  required_match INT DEFAULT 0,
  condition_expression CLOB,
  conditions_json CLOB,
  actions_json CLOB,
  fallback_action_json CLOB,
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  version_no INT NOT NULL DEFAULT 1,
  owner VARCHAR(80),
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_rule_condition (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT,
  condition_key VARCHAR(100),
  field VARCHAR(100),
  operator VARCHAR(32),
  value_json CLOB,
  expression CLOB,
  matched BOOLEAN,
  sort INT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_rule_action (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT,
  action_type VARCHAR(32),
  action_json CLOB,
  sort INT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_rule_set (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene_code VARCHAR(100) NOT NULL,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  strategy VARCHAR(32),
  required_match INT DEFAULT 0,
  short_circuit BOOLEAN DEFAULT FALSE,
  rule_codes_json CLOB,
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  version_no INT NOT NULL DEFAULT 1,
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_flow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene_code VARCHAR(100) NOT NULL,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  version_no INT NOT NULL DEFAULT 1,
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_flow_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  flow_id BIGINT NOT NULL,
  node_key VARCHAR(100) NOT NULL,
  type VARCHAR(32) NOT NULL,
  code VARCHAR(100),
  label VARCHAR(120),
  enabled BOOLEAN DEFAULT TRUE,
  sort INT DEFAULT 0,
  config_json CLOB,
  x INT DEFAULT 0,
  y INT DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_flow_edge (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  flow_id BIGINT NOT NULL,
  edge_key VARCHAR(100),
  source_key VARCHAR(100) NOT NULL,
  target_key VARCHAR(100) NOT NULL,
  branch VARCHAR(32),
  label VARCHAR(120),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_data_source (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  type VARCHAR(32),
  config_json CLOB,
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_model_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  provider VARCHAR(80),
  config_json CLOB,
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_score_card (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene_code VARCHAR(100),
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  items_json CLOB,
  mapping_json CLOB,
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  version_no INT NOT NULL DEFAULT 1,
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_table (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene_code VARCHAR(100),
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  hit_policy VARCHAR(32),
  rows_json CLOB,
  status VARCHAR(32) NOT NULL DEFAULT '草稿',
  version_no INT NOT NULL DEFAULT 1,
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_publish_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT,
  code VARCHAR(100),
  version_no INT,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  workflow_business_key VARCHAR(120),
  process_instance_id VARCHAR(120),
  workflow_model_id BIGINT,
  applicant VARCHAR(80),
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_gray_policy (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene_code VARCHAR(100),
  target_type VARCHAR(32),
  target_code VARCHAR(100),
  version_no INT,
  percent INT DEFAULT 0,
  condition_json CLOB,
  enabled BOOLEAN DEFAULT FALSE,
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_hit_detail_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  trace_id VARCHAR(80) NOT NULL,
  scene_code VARCHAR(100),
  target_type VARCHAR(32),
  target_code VARCHAR(100),
  detail_type VARCHAR(32),
  expression CLOB,
  matched BOOLEAN,
  detail_json CLOB,
  elapsed_ms BIGINT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_simulation_job (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene_code VARCHAR(100),
  name VARCHAR(120),
  status VARCHAR(32) DEFAULT 'DRAFT',
  sample_json CLOB,
  result_json CLOB,
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
