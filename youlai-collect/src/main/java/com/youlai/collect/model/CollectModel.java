package com.youlai.collect.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_model")
public class CollectModel extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelName;
    private String modelCode;
    private Long targetDataSourceId;
    private String targetTableName;
    private String status;
    private Integer fieldCount;
    private String remark;
}
