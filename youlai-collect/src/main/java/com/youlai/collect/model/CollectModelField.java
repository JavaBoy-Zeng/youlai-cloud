package com.youlai.collect.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_model_field")
public class CollectModelField extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private String fieldName;
    private String fieldCode;
    private String fieldType;
    private Integer requiredFlag;
    private Integer uniqueFlag;
    private String defaultValue;
    private Integer lengthLimit;
    private String formatRule;
    private String dictTypeCode;
    private Integer sort;
}
