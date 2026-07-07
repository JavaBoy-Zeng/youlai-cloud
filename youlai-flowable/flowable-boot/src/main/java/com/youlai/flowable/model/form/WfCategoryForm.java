package com.youlai.flowable.model.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WfCategoryForm {

    private Long id;

    private Long parentId = 0L;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    @NotBlank(message = "分类编码不能为空")
    private String code;

    private Integer sort = 0;

    private Integer status = 1;
}
