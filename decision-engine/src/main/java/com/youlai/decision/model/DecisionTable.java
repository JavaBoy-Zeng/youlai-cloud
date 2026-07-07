package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_table")
public class DecisionTable extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sceneCode;
    private String code;
    private String name;
    private String hitPolicy = "FIRST";
    private String rowsJson = "[]";
    private String status = DecisionStatus.DRAFT;
    private Integer versionNo = 1;
    private String remark;
}
