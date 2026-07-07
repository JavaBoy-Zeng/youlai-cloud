package com.youlai.demo.monitor.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Demo 项目视图对象。
 */
@Data
@Builder
public class DemoProjectVO {

    private Long id;

    private String name;

    private String owner;

    private String status;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
