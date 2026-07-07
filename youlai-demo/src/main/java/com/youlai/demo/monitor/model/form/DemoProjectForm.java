package com.youlai.demo.monitor.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Demo 项目表单。
 */
@Data
public class DemoProjectForm {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    @NotBlank(message = "负责人不能为空")
    private String owner;

    @Pattern(regexp = "TODO|DOING|DONE", message = "项目状态仅支持 TODO、DOING、DONE")
    private String status = "TODO";

    private String description;
}
