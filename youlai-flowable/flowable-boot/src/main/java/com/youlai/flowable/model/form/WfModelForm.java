package com.youlai.flowable.model.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WfModelForm {

    private Long id;

    private Long categoryId;

    @NotBlank(message = "流程编码不能为空")
    private String modelKey;

    @NotBlank(message = "流程名称不能为空")
    private String name;

    private String formKey;

    private String bpmnXml;

    private String configJson;

    private String remark;
}
