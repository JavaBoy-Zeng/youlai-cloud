-- youlai-collect 菜单迁移脚本
-- 已初始化 youlai_system 库的环境可单独执行本脚本。

USE youlai_system;

UPDATE sys_menu
SET sort = 8,
    update_time = NOW()
WHERE id = 20
  AND parent_id = 0;

INSERT INTO sys_menu
VALUES (100, 0, 2, '数据采集', '/collect', 'Layout', NULL, 'api', 7, 1, '/collect/models', '0', 1, NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  type = VALUES(type),
  name = VALUES(name),
  path = VALUES(path),
  component = VALUES(component),
  perm = VALUES(perm),
  icon = VALUES(icon),
  sort = VALUES(sort),
  visible = VALUES(visible),
  redirect = VALUES(redirect),
  tree_path = VALUES(tree_path),
  always_show = VALUES(always_show),
  keep_alive = VALUES(keep_alive),
  update_time = NOW();

INSERT INTO sys_menu
VALUES (101, 100, 1, '采集模型', 'models', 'collect/console/index', NULL, 'tree', 1, 1, NULL, '0,100', NULL, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), tree_path = VALUES(tree_path), keep_alive = VALUES(keep_alive), update_time = NOW();

INSERT INTO sys_menu
VALUES (102, 100, 1, '采集接口', 'apis', 'collect/console/index', NULL, 'api', 2, 1, NULL, '0,100', NULL, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), tree_path = VALUES(tree_path), keep_alive = VALUES(keep_alive), update_time = NOW();

INSERT INTO sys_menu
VALUES (103, 100, 1, 'DB 数据源', 'db-sources', 'collect/console/index', NULL, 'redis', 3, 1, NULL, '0,100', NULL, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), tree_path = VALUES(tree_path), keep_alive = VALUES(keep_alive), update_time = NOW();

INSERT INTO sys_menu
VALUES (104, 100, 1, '采集任务', 'tasks', 'collect/console/index', NULL, 'todolist', 4, 1, NULL, '0,100', NULL, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), tree_path = VALUES(tree_path), keep_alive = VALUES(keep_alive), update_time = NOW();

INSERT INTO sys_menu
VALUES (105, 100, 1, '执行实例', 'instances', 'collect/console/index', NULL, 'monitor', 5, 1, NULL, '0,100', NULL, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), tree_path = VALUES(tree_path), keep_alive = VALUES(keep_alive), update_time = NOW();

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 100 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 100);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 101 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 101);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 102 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 102);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 103 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 103);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 104 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 104);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 105 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 105);

ALTER TABLE sys_menu AUTO_INCREMENT = 106;
