/*
* youlai-flowable 工作流模块 SQL 脚本
* Flowable 引擎表由 Flowable 官方 DDL 或 flowable.database-schema-update 创建。
* 注意：flowable.database-schema-update 只能在空库里可靠自动建表；
* 如果本脚本先创建了业务表，Flowable 会把非空 schema 误判成旧版引擎库并尝试 upgrade。
* 因此初始化顺序应为：先创建 Flowable 引擎表，再执行本业务表脚本。
* 如果启动时报 ACT_GE_PROPERTY 不存在且 Flowable 正在执行 upgrade，
* 请先执行 docs/sql/youlai_flowable_flowable_engine_reset.sql 清理引擎表，
* 再执行 Flowable 7.2.0 官方 MySQL create DDL 创建引擎表。
* 以下为业务侧流程分类、模型、实例、审批记录表。
*/

CREATE DATABASE IF NOT EXISTS youlai_flowable DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
use youlai_flowable;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `wf_category`;
CREATE TABLE `wf_category` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                               `parent_id` bigint DEFAULT 0 COMMENT '父分类ID',
                               `name` varchar(64) NOT NULL COMMENT '分类名称',
                               `code` varchar(64) NOT NULL COMMENT '分类编码',
                               `sort` int DEFAULT 0 COMMENT '排序',
                               `status` tinyint DEFAULT 1 COMMENT '状态(1:启用;0:停用)',
                               `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                               `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_wf_category_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程分类';

DROP TABLE IF EXISTS `wf_model`;
CREATE TABLE `wf_model` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                            `category_id` bigint DEFAULT NULL COMMENT '流程分类ID',
                            `model_key` varchar(100) NOT NULL COMMENT '流程编码',
                            `name` varchar(100) NOT NULL COMMENT '流程名称',
                            `version` int DEFAULT 1 COMMENT '版本',
                            `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/PUBLISHED/SUSPENDED)',
                            `form_key` varchar(100) DEFAULT NULL COMMENT '发起表单标识',
                            `bpmn_xml` longtext COMMENT 'BPMN XML',
                            `config_json` longtext COMMENT '节点/按钮/字段/审批人配置JSON',
                            `deployment_id` varchar(64) DEFAULT NULL COMMENT 'Flowable部署ID',
                            `process_definition_id` varchar(128) DEFAULT NULL COMMENT 'Flowable流程定义ID',
                            `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                            `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                            `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_wf_model_key` (`model_key`),
                            KEY `idx_wf_model_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程模型';

DROP TABLE IF EXISTS `wf_instance`;
CREATE TABLE `wf_instance` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                               `process_instance_id` varchar(64) NOT NULL COMMENT 'Flowable流程实例ID',
                               `process_definition_id` varchar(128) NOT NULL COMMENT 'Flowable流程定义ID',
                               `business_key` varchar(128) DEFAULT NULL COMMENT '业务键',
                               `model_id` bigint DEFAULT NULL COMMENT '流程模型ID',
                               `model_key` varchar(100) DEFAULT NULL COMMENT '流程编码',
                               `model_name` varchar(100) DEFAULT NULL COMMENT '流程名称',
                               `starter_id` bigint DEFAULT NULL COMMENT '发起人ID',
                               `starter_username` varchar(64) DEFAULT NULL COMMENT '发起用户名',
                               `status` varchar(32) DEFAULT 'RUNNING' COMMENT '状态(RUNNING/COMPLETED/TERMINATED/REVOKED)',
                               `form_key` varchar(100) DEFAULT NULL COMMENT '表单标识',
                               `form_data_json` longtext COMMENT '表单数据JSON',
                               `current_node_name` varchar(255) DEFAULT NULL COMMENT '当前节点名称',
                               `start_time` datetime DEFAULT NULL COMMENT '发起时间',
                               `end_time` datetime DEFAULT NULL COMMENT '结束时间',
                               `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                               `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_wf_instance_process` (`process_instance_id`),
                               KEY `idx_wf_instance_starter` (`starter_id`),
                               KEY `idx_wf_instance_business` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程实例';

DROP TABLE IF EXISTS `wf_task_record`;
CREATE TABLE `wf_task_record` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                  `task_id` varchar(64) DEFAULT NULL COMMENT 'Flowable任务ID',
                                  `process_instance_id` varchar(64) NOT NULL COMMENT 'Flowable流程实例ID',
                                  `task_name` varchar(100) DEFAULT NULL COMMENT '任务名称',
                                  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
                                  `operator_username` varchar(64) DEFAULT NULL COMMENT '操作用户名',
                                  `action` varchar(32) DEFAULT NULL COMMENT '操作动作',
                                  `comment` varchar(1000) DEFAULT NULL COMMENT '审批意见',
                                  `attachment_json` longtext COMMENT '附件JSON',
                                  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_wf_task_record_process` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批记录';

INSERT INTO `wf_category` (`id`, `parent_id`, `name`, `code`, `sort`, `status`, `create_time`, `update_time`)
VALUES (1, 0, '通用审批', 'general', 1, 1, NOW(), NOW());

DROP TABLE IF EXISTS `app_builder_app`;
CREATE TABLE `app_builder_app` (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                   `app_code` varchar(100) NOT NULL COMMENT '应用编码',
                                   `app_name` varchar(100) NOT NULL COMMENT '应用名称',
                                   `app_desc` varchar(500) DEFAULT NULL COMMENT '应用描述',
                                   `app_icon` varchar(100) DEFAULT NULL COMMENT '应用图标',
                                   `category` varchar(64) DEFAULT NULL COMMENT '应用分类',
                                   `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/PUBLISHED/DISABLED)',
                                   `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                   `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                   `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_app_builder_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-应用';

DROP TABLE IF EXISTS `app_builder_model`;
CREATE TABLE `app_builder_model` (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                     `app_id` bigint NOT NULL COMMENT '所属应用ID',
                                     `model_code` varchar(100) NOT NULL COMMENT '模型编码',
                                     `model_name` varchar(100) NOT NULL COMMENT '模型名称',
                                     `table_name` varchar(100) DEFAULT NULL COMMENT '逻辑表名/预留物理表名',
                                     `main_field` varchar(100) DEFAULT NULL COMMENT '主显示字段',
                                     `enable_flow` tinyint DEFAULT 0 COMMENT '是否启用流程',
                                     `form_key` varchar(100) DEFAULT NULL COMMENT '绑定表单标识',
                                     `process_key` varchar(100) DEFAULT NULL COMMENT '绑定流程编码',
                                     `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/PUBLISHED)',
                                     `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                     `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                     `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_app_builder_model_code` (`app_id`, `model_code`),
                                     KEY `idx_app_builder_model_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-数据模型';

DROP TABLE IF EXISTS `app_builder_model_field`;
CREATE TABLE `app_builder_model_field` (
                                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                           `model_id` bigint NOT NULL COMMENT '模型ID',
                                           `field_code` varchar(100) NOT NULL COMMENT '字段编码',
                                           `field_name` varchar(100) NOT NULL COMMENT '字段名称',
                                           `field_type` varchar(64) DEFAULT NULL COMMENT '字段类型',
                                           `db_type` varchar(64) DEFAULT 'varchar' COMMENT '数据库字段类型',
                                           `required` tinyint DEFAULT 0 COMMENT '是否必填',
                                           `default_value` varchar(500) DEFAULT NULL COMMENT '默认值',
                                           `options_json` text COMMENT '选项配置JSON',
                                           `validate_json` text COMMENT '校验规则JSON',
                                           `sort_order` int DEFAULT 0 COMMENT '排序',
                                           `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                           `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                           PRIMARY KEY (`id`),
                                           KEY `idx_app_builder_model_field_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-模型字段';

DROP TABLE IF EXISTS `app_builder_model_field_version`;
CREATE TABLE `app_builder_model_field_version` (
                                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                                   `model_id` bigint NOT NULL COMMENT '模型ID',
                                                   `version_no` int NOT NULL COMMENT '字段版本号',
                                                   `fields_snapshot_json` longtext COMMENT '字段快照JSON',
                                                   `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                                   `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                                   `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                                   PRIMARY KEY (`id`),
                                                   KEY `idx_app_builder_field_version_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-模型字段版本';

DROP TABLE IF EXISTS `app_builder_form`;
CREATE TABLE `app_builder_form` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                    `app_id` bigint DEFAULT NULL COMMENT '应用ID，应用管理落地后绑定',
                                    `model_id` bigint DEFAULT NULL COMMENT '数据模型ID，数据建模落地后绑定',
                                    `form_key` varchar(100) NOT NULL COMMENT '表单标识',
                                    `form_name` varchar(100) NOT NULL COMMENT '表单名称',
                                    `form_schema` longtext NOT NULL COMMENT '表单JSON Schema',
                                    `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/PUBLISHED)',
                                    `version` int DEFAULT 1 COMMENT '版本',
                                    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除；删除后写入主键ID)',
                                    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_app_builder_form_key` (`form_key`, `deleted`),
                                    KEY `idx_app_builder_form_app` (`app_id`),
                                    KEY `idx_app_builder_form_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建表单配置';

DROP TABLE IF EXISTS `app_builder_page`;
CREATE TABLE `app_builder_page` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                    `app_id` bigint DEFAULT NULL COMMENT '应用ID',
                                    `model_id` bigint NOT NULL COMMENT '模型ID',
                                    `page_type` varchar(32) NOT NULL COMMENT '页面类型(LIST/DETAIL/FORM)',
                                    `page_name` varchar(100) NOT NULL COMMENT '页面名称',
                                    `page_schema` longtext COMMENT '页面JSON Schema',
                                    `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/PUBLISHED)',
                                    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_app_builder_page_app` (`app_id`),
                                    KEY `idx_app_builder_page_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-页面配置';

DROP TABLE IF EXISTS `app_builder_data`;
CREATE TABLE `app_builder_data` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                    `app_id` bigint DEFAULT NULL COMMENT '应用ID',
                                    `model_id` bigint NOT NULL COMMENT '模型ID',
                                    `business_key` varchar(128) DEFAULT NULL COMMENT '业务键',
                                    `status` varchar(32) DEFAULT 'DRAFT' COMMENT '数据状态',
                                    `data_json` longtext COMMENT '业务数据JSON',
                                    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_app_builder_data_app` (`app_id`),
                                    KEY `idx_app_builder_data_model` (`model_id`),
                                    KEY `idx_app_builder_data_business` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-业务数据';

DROP TABLE IF EXISTS `app_builder_menu`;
CREATE TABLE `app_builder_menu` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                    `app_id` bigint NOT NULL COMMENT '应用ID',
                                    `page_id` bigint NOT NULL COMMENT '页面ID',
                                    `menu_name` varchar(100) NOT NULL COMMENT '菜单名称',
                                    `route_path` varchar(255) NOT NULL COMMENT '运行路由',
                                    `route_name` varchar(100) DEFAULT NULL COMMENT '路由名称',
                                    `component` varchar(255) DEFAULT NULL COMMENT '前端组件',
                                    `perm` varchar(255) DEFAULT NULL COMMENT '权限标识',
                                    `icon` varchar(64) DEFAULT NULL COMMENT '图标',
                                    `visible` tinyint DEFAULT 1 COMMENT '是否可见',
                                    `sort_order` int DEFAULT 0 COMMENT '排序',
                                    `status` varchar(32) DEFAULT 'ENABLED' COMMENT '状态',
                                    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_app_builder_menu_page` (`page_id`),
                                    KEY `idx_app_builder_menu_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-运行菜单';

DROP TABLE IF EXISTS `app_builder_report`;
CREATE TABLE `app_builder_report` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                      `app_id` bigint DEFAULT NULL COMMENT '应用ID',
                                      `model_id` bigint DEFAULT NULL COMMENT '模型ID',
                                      `report_name` varchar(100) NOT NULL COMMENT '报表名称',
                                      `report_type` varchar(32) DEFAULT 'CHART' COMMENT '报表类型',
                                      `chart_type` varchar(32) DEFAULT 'bar' COMMENT '图表类型',
                                      `data_source_json` longtext COMMENT '数据源配置JSON',
                                      `chart_schema` longtext COMMENT '图表配置JSON',
                                      `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态',
                                      `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                      `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                      `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_app_builder_report_app` (`app_id`),
                                      KEY `idx_app_builder_report_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-报表仪表盘';

DROP TABLE IF EXISTS `app_builder_api`;
CREATE TABLE `app_builder_api` (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                   `app_id` bigint DEFAULT NULL COMMENT '应用ID',
                                   `api_name` varchar(100) NOT NULL COMMENT '接口名称',
                                   `api_code` varchar(100) DEFAULT NULL COMMENT '接口编码',
                                   `method` varchar(16) DEFAULT 'GET' COMMENT '请求方法',
                                   `url` varchar(500) NOT NULL COMMENT '接口地址',
                                   `headers_json` text COMMENT '请求头JSON',
                                   `params_json` text COMMENT '参数映射JSON',
                                   `body_template` longtext COMMENT '请求体模板',
                                   `retry_times` int DEFAULT 0 COMMENT '失败重试次数',
                                   `timeout_ms` int DEFAULT 10000 COMMENT '调用超时时间毫秒',
                                   `status` varchar(32) DEFAULT 'ENABLED' COMMENT '状态',
                                   `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                   `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                   `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                   PRIMARY KEY (`id`),
                                   KEY `idx_app_builder_api_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-API集成';

DROP TABLE IF EXISTS `app_builder_api_log`;
CREATE TABLE `app_builder_api_log` (
                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                       `api_id` bigint DEFAULT NULL COMMENT '接口ID',
                                       `request_json` longtext COMMENT '请求JSON',
                                       `response_text` longtext COMMENT '响应内容',
                                       `status_code` int DEFAULT NULL COMMENT 'HTTP状态码',
                                       `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
                                       `success` tinyint DEFAULT 1 COMMENT '是否成功',
                                       `error_msg` varchar(1000) DEFAULT NULL COMMENT '错误信息',
                                       `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                       `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_app_builder_api_log_api` (`api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-API调用日志';

DROP TABLE IF EXISTS `app_builder_automation`;
CREATE TABLE `app_builder_automation` (
                                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                          `app_id` bigint DEFAULT NULL COMMENT '应用ID',
                                          `model_id` bigint DEFAULT NULL COMMENT '模型ID',
                                          `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
                                          `trigger_type` varchar(64) DEFAULT NULL COMMENT '触发类型',
                                          `trigger_config_json` longtext COMMENT '触发配置JSON',
                                          `action_type` varchar(64) DEFAULT NULL COMMENT '动作类型',
                                          `action_config_json` longtext COMMENT '动作配置JSON',
                                          `status` varchar(32) DEFAULT 'ENABLED' COMMENT '状态',
                                          `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                          `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                          `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                          PRIMARY KEY (`id`),
                                          KEY `idx_app_builder_automation_app` (`app_id`),
                                          KEY `idx_app_builder_automation_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-自动化工作流';

DROP TABLE IF EXISTS `app_builder_notification`;
CREATE TABLE `app_builder_notification` (
                                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                            `app_id` bigint DEFAULT NULL COMMENT '应用ID',
                                            `automation_id` bigint DEFAULT NULL COMMENT '自动化规则ID',
                                            `receiver_id` bigint DEFAULT NULL COMMENT '接收人ID',
                                            `receiver_username` varchar(64) DEFAULT NULL COMMENT '接收人用户名',
                                            `title` varchar(200) NOT NULL COMMENT '标题',
                                            `content` longtext COMMENT '内容',
                                            `status` varchar(32) DEFAULT 'UNREAD' COMMENT '状态(UNREAD/READ)',
                                            `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                            `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                            `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                            PRIMARY KEY (`id`),
                                            KEY `idx_app_builder_notification_app` (`app_id`),
                                            KEY `idx_app_builder_notification_receiver` (`receiver_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-通知消息';

DROP TABLE IF EXISTS `app_builder_template`;
CREATE TABLE `app_builder_template` (
                                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                        `template_name` varchar(100) NOT NULL COMMENT '模板名称',
                                        `template_code` varchar(100) NOT NULL COMMENT '模板编码',
                                        `category` varchar(64) DEFAULT NULL COMMENT '模板分类',
                                        `cover_url` varchar(500) DEFAULT NULL COMMENT '封面地址',
                                        `config_json` longtext COMMENT '模板配置JSON',
                                        `status` varchar(32) DEFAULT 'PUBLISHED' COMMENT '状态',
                                        `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                        `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                        `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `uk_app_builder_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-模板中心';

DROP TABLE IF EXISTS `app_builder_version`;
CREATE TABLE `app_builder_version` (
                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                       `app_id` bigint DEFAULT NULL COMMENT '应用ID',
                                       `version_no` varchar(64) NOT NULL COMMENT '版本号',
                                       `version_name` varchar(100) DEFAULT NULL COMMENT '版本名称',
                                       `config_snapshot_json` longtext COMMENT '配置快照JSON',
                                       `publish_status` varchar(32) DEFAULT 'DRAFT' COMMENT '发布状态',
                                       `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                       `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                       `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_app_builder_version_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-版本管理';

DROP TABLE IF EXISTS `app_builder_tenant`;
CREATE TABLE `app_builder_tenant` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                      `tenant_code` varchar(100) NOT NULL COMMENT '租户编码',
                                      `tenant_name` varchar(100) NOT NULL COMMENT '租户名称',
                                      `plan_code` varchar(64) DEFAULT NULL COMMENT '套餐编码',
                                      `isolation_mode` varchar(64) DEFAULT 'SHARED_SCHEMA' COMMENT '隔离模式',
                                      `status` varchar(32) DEFAULT 'ENABLED' COMMENT '状态',
                                      `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                      `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                      `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_app_builder_tenant_code` (`tenant_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-租户';

DROP TABLE IF EXISTS `app_builder_operation_log`;
CREATE TABLE `app_builder_operation_log` (
                                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                             `app_id` bigint DEFAULT NULL COMMENT '应用ID',
                                             `module_name` varchar(100) DEFAULT NULL COMMENT '模块名称',
                                             `operation_type` varchar(64) DEFAULT NULL COMMENT '操作类型',
                                             `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
                                             `content_json` longtext COMMENT '操作内容JSON',
                                             `success` tinyint DEFAULT 1 COMMENT '是否成功',
                                             `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                             `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                             `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                             PRIMARY KEY (`id`),
                                             KEY `idx_app_builder_operation_log_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用搭建-操作日志';

SET FOREIGN_KEY_CHECKS = 1;
