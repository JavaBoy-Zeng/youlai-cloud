/*
 * 决策引擎历史英文状态中文化迁移脚本。
 *
 * 可重复执行，只会把旧英文状态转换为中文状态：
 * DRAFT     -> 草稿
 * ENABLED   -> 已启用
 * PUBLISHED -> 已发布
 * DISABLED  -> 已停用
 */

USE youlai_cloud;

UPDATE decision_artifact
SET status = CASE status
    WHEN 'DRAFT' THEN '草稿'
    WHEN 'ENABLED' THEN '已启用'
    WHEN 'PUBLISHED' THEN '已发布'
    WHEN 'DISABLED' THEN '已停用'
    ELSE status
END
WHERE status IN ('DRAFT', 'ENABLED', 'PUBLISHED', 'DISABLED');

UPDATE decision_publish_record
SET status = CASE status
    WHEN 'DRAFT' THEN '草稿'
    WHEN 'ENABLED' THEN '已启用'
    WHEN 'PUBLISHED' THEN '已发布'
    WHEN 'DISABLED' THEN '已停用'
    ELSE status
END
WHERE status IN ('DRAFT', 'ENABLED', 'PUBLISHED', 'DISABLED');

SELECT status, COUNT(*) AS count
FROM decision_artifact
GROUP BY status
ORDER BY status;

SELECT status, COUNT(*) AS count
FROM decision_publish_record
GROUP BY status
ORDER BY status;
