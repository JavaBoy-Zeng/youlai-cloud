package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_score_card")
public class DecisionScoreCard extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sceneCode;
    private String code;
    private String name;
    private String itemsJson = "[]";
    private String mappingJson = "[]";
    private String status = DecisionStatus.DRAFT;
    private Integer versionNo = 1;
    private String remark;
}
