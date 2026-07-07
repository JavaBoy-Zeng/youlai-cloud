package com.youlai.flowable.model.vo;

import lombok.Data;

@Data
public class WfCategoryVO {

    private Long id;

    private Long parentId;

    private String name;

    private String code;

    private Integer sort;

    private Integer status;
}
