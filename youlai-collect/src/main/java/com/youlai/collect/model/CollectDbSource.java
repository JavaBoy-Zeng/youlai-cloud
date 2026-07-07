package com.youlai.collect.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_db_source")
public class CollectDbSource extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceName;
    private String dbType;
    private String jdbcUrl;
    private String driverClass;
    private String username;
    private String passwordEncrypt;
    private Integer connectTimeout;
    private Integer queryTimeout;
    private String poolConfig;
    private Integer poolMinSize;
    private Integer poolMaxSize;
    private String validationQuery;
    private LocalDateTime lastTestTime;
    private String lastTestStatus;
    private String status;
}
