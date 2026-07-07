/*
 * youlai-demo SQL 脚本
 * MySQL 8.x
 */

-- ----------------------------
-- 创建数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS youlai_demo DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;

USE youlai_demo;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for demo_project
-- ----------------------------
DROP TABLE IF EXISTS `demo_project`;
CREATE TABLE `demo_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `owner` varchar(64) NOT NULL COMMENT '负责人',
  `status` varchar(20) NOT NULL DEFAULT 'TODO' COMMENT '项目状态：TODO/DOING/DONE',
  `description` varchar(500) DEFAULT NULL COMMENT '项目说明',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(1:已删除;0:未删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_demo_project_status` (`status`),
  KEY `idx_demo_project_owner` (`owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Demo 项目表';

-- ----------------------------
-- Records of demo_project
-- ----------------------------
INSERT INTO `demo_project` (`id`, `name`, `owner`, `status`, `description`, `deleted`, `create_time`, `update_time`)
VALUES
  (1, '权限菜单接入 Demo', 'admin', 'DONE', '演示 Controller、Service、Mapper 与统一响应结构。', 0, NOW(), NOW()),
  (2, '业务流程实现 Demo', 'demo', 'DOING', '演示如何从表单入参组织一段可落库的业务实现。', 0, NOW(), NOW());

SET FOREIGN_KEY_CHECKS = 1;
