package com.youlai.collect.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CollectModelDetail extends CollectModel {
    private List<CollectModelField> fields;

    /**
     * 根据模型主表和字段列表组装模型详情对象。
     */
    public static CollectModelDetail of(CollectModel model, List<CollectModelField> fields) {
        CollectModelDetail detail = new CollectModelDetail();
        BeanUtils.copyProperties(model, detail);
        detail.setFields(fields);
        return detail;
    }
}
