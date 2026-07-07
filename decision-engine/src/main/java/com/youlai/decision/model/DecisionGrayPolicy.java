package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_gray_policy")
public class DecisionGrayPolicy extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sceneCode;
    private String targetType;
    private String targetCode;
    private Integer versionNo;
    private Integer percent = 0;
    private String conditionJson = "{}";
    private Boolean enabled = false;
    private String remark;
}
