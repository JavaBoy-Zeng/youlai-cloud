package com.youlai.flowable.model.query;

import com.youlai.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WfModelPageQuery extends BasePageQuery {

    private Long categoryId;

    private String keywords;

    private String status;
}
