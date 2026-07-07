package com.youlai.flowable.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppBuilderFormVO {

    private Long id;

    private Long appId;

    private Long modelId;

    private String formKey;

    private String formName;

    private String formSchema;

    private String status;

    private Integer version;

    private String remark;

    private Long deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
