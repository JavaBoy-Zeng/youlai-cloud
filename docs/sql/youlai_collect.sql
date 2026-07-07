-- youlai-collect 数据采集 ETL 模块表结构
-- MySQL / MariaDB

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS collect_model (
  id BIGINT NOT NULL AUTO_INCREMENT,
  model_name VARCHAR(120) NOT NULL COMMENT '模型名称',
  model_code VARCHAR(100) NOT NULL COMMENT '模型编码',
  target_table_name VARCHAR(120) DEFAULT NULL COMMENT '目标表名',
  status VARCHAR(32) NOT NULL DEFAULT 'enabled' COMMENT '状态: enabled, disabled',
  field_count INT NOT NULL DEFAULT 0 COMMENT '字段数',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_collect_model_code (model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集模型主表';

CREATE TABLE IF NOT EXISTS collect_model_field (
  id BIGINT NOT NULL AUTO_INCREMENT,
  model_id BIGINT NOT NULL COMMENT '模型 ID',
  field_name VARCHAR(120) NOT NULL COMMENT '字段名称',
  field_code VARCHAR(100) NOT NULL COMMENT '字段编码',
  field_type VARCHAR(32) NOT NULL DEFAULT 'string' COMMENT '字段类型',
  required_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否必填',
  unique_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否唯一键',
  default_value VARCHAR(500) DEFAULT NULL COMMENT '默认值',
  length_limit INT DEFAULT NULL COMMENT '长度限制',
  format_rule VARCHAR(200) DEFAULT NULL COMMENT '格式规则',
  dict_type_code VARCHAR(100) DEFAULT NULL COMMENT '字典类型编码',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_collect_model_field_code (model_id, field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集模型字段表';

CREATE TABLE IF NOT EXISTS collect_api (
  id BIGINT NOT NULL AUTO_INCREMENT,
  api_name VARCHAR(120) NOT NULL COMMENT '接口名称',
  api_code VARCHAR(100) NOT NULL COMMENT '接口编码',
  collect_type VARCHAR(32) NOT NULL COMMENT '采集方式: http, db, mq',
  source_name VARCHAR(120) DEFAULT NULL COMMENT '来源名称',
  timeout_seconds INT DEFAULT 30 COMMENT '超时时间',
  max_fetch_count INT DEFAULT 1000 COMMENT '最大采集量',
  parse_config LONGTEXT COMMENT '解析配置 JSON',
  config_json LONGTEXT COMMENT '来源配置 JSON',
  status VARCHAR(32) NOT NULL DEFAULT 'enabled' COMMENT '状态',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_collect_api_code (api_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集接口主表';

CREATE TABLE IF NOT EXISTS collect_db_source (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_name VARCHAR(120) NOT NULL COMMENT '数据源名称',
  db_type VARCHAR(32) NOT NULL COMMENT '数据库类型',
  jdbc_url VARCHAR(500) NOT NULL COMMENT 'JDBC URL',
  driver_class VARCHAR(200) DEFAULT NULL COMMENT '驱动类',
  username VARCHAR(120) DEFAULT NULL COMMENT '用户名',
  password_encrypt VARCHAR(500) DEFAULT NULL COMMENT '密码/密文',
  connect_timeout INT DEFAULT 10 COMMENT '连接超时秒数',
  query_timeout INT DEFAULT 30 COMMENT '查询超时秒数',
  pool_config LONGTEXT COMMENT '连接池配置 JSON',
  pool_min_size INT DEFAULT 1 COMMENT '最小连接数',
  pool_max_size INT DEFAULT 5 COMMENT '最大连接数',
  validation_query VARCHAR(200) DEFAULT NULL COMMENT '连接校验 SQL',
  last_test_time DATETIME DEFAULT NULL COMMENT '最近测试时间',
  last_test_status VARCHAR(32) DEFAULT NULL COMMENT '最近测试状态',
  status VARCHAR(32) NOT NULL DEFAULT 'enabled' COMMENT '状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DB 数据源连接配置表';

CREATE TABLE IF NOT EXISTS collect_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_name VARCHAR(120) NOT NULL COMMENT '任务名称',
  task_code VARCHAR(100) NOT NULL COMMENT '任务编码',
  model_id BIGINT NOT NULL COMMENT '模型 ID',
  api_id BIGINT NOT NULL COMMENT '采集接口 ID',
  schedule_type VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT '调度类型: manual, cron',
  cron_expr VARCHAR(120) DEFAULT NULL COMMENT 'Cron 表达式',
  job_id BIGINT DEFAULT NULL COMMENT 'XXL-JOB 任务 ID',
  collect_mode VARCHAR(32) NOT NULL DEFAULT 'full' COMMENT '采集模式: full, increment',
  last_success_time DATETIME DEFAULT NULL COMMENT '上次成功时间',
  last_cursor VARCHAR(500) DEFAULT NULL COMMENT '上次游标',
  insert_strategy VARCHAR(32) NOT NULL DEFAULT 'insert' COMMENT '入库策略: insert, ignore, upsert, overwrite',
  max_fetch_count INT DEFAULT 1000 COMMENT '最大采集量',
  mapping_json LONGTEXT COMMENT '字段映射 JSON',
  transform_json LONGTEXT COMMENT '转换规则 JSON',
  status VARCHAR(32) NOT NULL DEFAULT 'draft' COMMENT '状态: draft, enabled, disabled, error',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_collect_task_code (task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集任务主表';

CREATE TABLE IF NOT EXISTS collect_instance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  job_log_id BIGINT DEFAULT NULL COMMENT 'XXL-JOB 日志 ID',
  trace_id VARCHAR(64) NOT NULL COMMENT 'Trace ID',
  mq_message_id VARCHAR(120) DEFAULT NULL COMMENT '内部消息 ID',
  trigger_type VARCHAR(32) NOT NULL COMMENT '触发方式: manual, xxljob, retry',
  status VARCHAR(32) NOT NULL COMMENT '实例状态',
  start_time DATETIME DEFAULT NULL COMMENT '开始时间',
  end_time DATETIME DEFAULT NULL COMMENT '结束时间',
  total_count INT DEFAULT 0 COMMENT '总数',
  valid_count INT DEFAULT 0 COMMENT '有效数',
  invalid_count INT DEFAULT 0 COMMENT '异常数',
  duplicate_count INT DEFAULT 0 COMMENT '重复数',
  inserted_count INT DEFAULT 0 COMMENT '新增数',
  updated_count INT DEFAULT 0 COMMENT '更新数',
  failed_count INT DEFAULT 0 COMMENT '失败数',
  error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_collect_instance_task_id (task_id),
  KEY idx_collect_instance_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集任务执行实例表';

CREATE TABLE IF NOT EXISTS collect_task_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  instance_id BIGINT NOT NULL COMMENT '实例 ID',
  trace_id VARCHAR(64) NOT NULL COMMENT 'Trace ID',
  mq_topic VARCHAR(120) DEFAULT NULL COMMENT '内部消息 Topic',
  mq_message_id VARCHAR(120) DEFAULT NULL COMMENT '内部消息 ID',
  message_body LONGTEXT COMMENT '消息体',
  send_status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '投递状态',
  consume_status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '消费状态',
  send_time DATETIME DEFAULT NULL COMMENT '投递时间',
  consume_time DATETIME DEFAULT NULL COMMENT '消费开始时间',
  finish_time DATETIME DEFAULT NULL COMMENT '完成时间',
  error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_collect_message_instance_id (instance_id),
  KEY idx_collect_message_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部任务消息记录表';

CREATE TABLE IF NOT EXISTS collect_raw_data (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  instance_id BIGINT NOT NULL COMMENT '实例 ID',
  trace_id VARCHAR(64) NOT NULL COMMENT 'Trace ID',
  data_index INT NOT NULL DEFAULT 0 COMMENT '数据序号',
  raw_body LONGTEXT COMMENT '原始数据',
  status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '处理状态',
  error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_collect_raw_instance_id (instance_id),
  KEY idx_collect_raw_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原始采集数据表';

CREATE TABLE IF NOT EXISTS collect_error_data (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  instance_id BIGINT NOT NULL COMMENT '实例 ID',
  trace_id VARCHAR(64) NOT NULL COMMENT 'Trace ID',
  data_index INT NOT NULL DEFAULT 0 COMMENT '数据序号',
  error_type VARCHAR(64) NOT NULL COMMENT '异常类型',
  error_message VARCHAR(1000) DEFAULT NULL COMMENT '异常原因',
  raw_body LONGTEXT COMMENT '原始数据',
  transformed_body LONGTEXT COMMENT '转换后数据',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_collect_error_instance_id (instance_id),
  KEY idx_collect_error_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常数据明细表';

CREATE TABLE IF NOT EXISTS collect_quality_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  instance_id BIGINT NOT NULL COMMENT '实例 ID',
  trace_id VARCHAR(64) NOT NULL COMMENT 'Trace ID',
  total_count INT NOT NULL DEFAULT 0 COMMENT '总数',
  valid_count INT NOT NULL DEFAULT 0 COMMENT '有效数',
  invalid_count INT NOT NULL DEFAULT 0 COMMENT '异常数',
  duplicate_count INT NOT NULL DEFAULT 0 COMMENT '重复数',
  inserted_count INT NOT NULL DEFAULT 0 COMMENT '新增数',
  updated_count INT NOT NULL DEFAULT 0 COMMENT '更新数',
  failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
  field_completeness_json LONGTEXT COMMENT '字段完整率 JSON',
  summary_json LONGTEXT COMMENT '摘要 JSON',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_collect_quality_instance_id (instance_id),
  KEY idx_collect_quality_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量分析表';
