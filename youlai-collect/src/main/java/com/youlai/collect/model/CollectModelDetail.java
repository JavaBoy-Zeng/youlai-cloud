package com.youlai.collect.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CollectModelDetail extends CollectModel {
    private List<CollectModelField> fields;

    public static CollectModelDetail of(CollectModel model, List<CollectModelField> fields) {
        CollectModelDetail detail = new CollectModelDetail();
        BeanUtils.copyProperties(model, detail);
        detail.setFields(fields);
        return detail;
    }
}
