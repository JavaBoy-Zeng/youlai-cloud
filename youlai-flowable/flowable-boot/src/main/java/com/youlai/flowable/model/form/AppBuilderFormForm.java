package com.youlai.flowable.model.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppBuilderFormForm {

    private Long id;

    private Long appId;

    private Long modelId;

    @NotBlank(message = "表单标识不能为空")
    private String formKey;

    @NotBlank(message = "表单名称不能为空")
    private String formName;

    @NotBlank(message = "表单Schema不能为空")
    private String formSchema;

    private String status;

    private String remark;
}
