package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_scene")
public class DecisionScene extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String category;
    private String status = DecisionStatus.DRAFT;
    private String inputSchemaJson = "{}";
    private String outputSchemaJson = "{}";
    private String owner;
    private String remark;
}
