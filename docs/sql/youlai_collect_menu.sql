-- youlai-collect 菜单删除脚本
-- 已初始化 youlai_system 库的环境可单独执行本脚本。

USE youlai_system;

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.path = '/collect'
   OR m.tree_path = '0,100'
   OR m.tree_path LIKE '0,100,%'
   OR m.component = 'collect/console/index';

DELETE FROM sys_role_menu
WHERE menu_id IN (100, 101, 102, 103, 104, 105, 106);

DELETE FROM sys_menu
WHERE id IN (100, 101, 102, 103, 104, 105, 106)
   OR tree_path = '0,100'
   OR tree_path LIKE '0,100,%'
   OR path = '/collect'
   OR component = 'collect/console/index';
