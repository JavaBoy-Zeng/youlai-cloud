/*
* Reset Flowable engine-owned tables for the youlai-flowable module.
*
* Use this only for local/dev databases, or after backing up production data.
* It fixes a partial/old Flowable schema where startup tries to upgrade from
* an older version but ACT_GE_PROPERTY is missing.
*
* After running this script, the final query should return 0 rows.
* If this schema still contains wf_* or app_builder_* business tables, do not
* rely on flowable.database-schema-update to recreate the engine tables.
* Run Flowable 7.2.0 official MySQL create DDL first, then start youlai-flowable.
*/

CREATE DATABASE IF NOT EXISTS youlai_flowable DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
use youlai_flowable;

SET FOREIGN_KEY_CHECKS = 0;

SET SESSION group_concat_max_len = 102400;

SELECT GROUP_CONCAT(
           CONCAT('`', REPLACE(TABLE_NAME, '`', '``'), '`')
           ORDER BY TABLE_NAME
           SEPARATOR ', '
       )
INTO @flowable_engine_tables
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND (
      UPPER(TABLE_NAME) LIKE 'ACT\\_%'
      OR UPPER(TABLE_NAME) LIKE 'FLW\\_%'
  );

SET @drop_flowable_engine_tables = IF(
    @flowable_engine_tables IS NULL,
    'SELECT ''No Flowable engine tables found'' AS message',
    CONCAT('DROP TABLE IF EXISTS ', @flowable_engine_tables)
);

PREPARE drop_flowable_engine_tables_stmt FROM @drop_flowable_engine_tables;
EXECUTE drop_flowable_engine_tables_stmt;
DEALLOCATE PREPARE drop_flowable_engine_tables_stmt;

SET FOREIGN_KEY_CHECKS = 1;

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND (
      UPPER(TABLE_NAME) LIKE 'ACT\\_%'
      OR UPPER(TABLE_NAME) LIKE 'FLW\\_%'
  )
ORDER BY TABLE_NAME;
