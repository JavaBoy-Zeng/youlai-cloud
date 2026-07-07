package com.youlai.demo.monitor.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Demo 项目实体。
 */
@Data
@TableName("demo_project")
public class DemoProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String owner;

    private String status;

    private String description;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
