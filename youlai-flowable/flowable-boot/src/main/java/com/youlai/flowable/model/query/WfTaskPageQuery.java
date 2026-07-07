package com.youlai.flowable.model.query;

import com.youlai.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WfTaskPageQuery extends BasePageQuery {

    private String keywords;
}
