USE youlai_flowable;

SET @column_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_builder_form'
      AND COLUMN_NAME = 'deleted'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE `app_builder_form` ADD COLUMN `deleted` bigint NOT NULL DEFAULT 0 COMMENT ''逻辑删除标识(0-未删除；删除后写入主键ID)'' AFTER `remark`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_builder_form'
      AND INDEX_NAME = 'uk_app_builder_form_key'
);
SET @sql := IF(
    @index_exists > 0,
    'ALTER TABLE `app_builder_form` DROP INDEX `uk_app_builder_form_key`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `app_builder_form`
    ADD UNIQUE KEY `uk_app_builder_form_key` (`form_key`, `deleted`);
