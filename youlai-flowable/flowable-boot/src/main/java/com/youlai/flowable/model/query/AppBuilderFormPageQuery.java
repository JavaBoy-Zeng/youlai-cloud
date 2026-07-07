package com.youlai.flowable.model.query;

import com.youlai.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppBuilderFormPageQuery extends BasePageQuery {

    private Long appId;

    private Long modelId;

    private String formKey;

    private String formName;

    private String keywords;

    private String status;
}
