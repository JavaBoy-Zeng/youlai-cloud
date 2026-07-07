package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_variable")
public class DecisionVariable extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sceneCode;
    private String code;
    private String name;
    private String type;
    private String source;
    private String sourceConfigJson = "{}";
    private String defaultValueJson;
    private String status = DecisionStatus.DRAFT;
    private String remark;
}
