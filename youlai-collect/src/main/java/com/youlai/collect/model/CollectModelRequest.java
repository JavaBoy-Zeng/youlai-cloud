package com.youlai.collect.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CollectModelRequest {
    private Long id;
    @NotBlank(message = "模型名称不能为空")
    private String modelName;
    @NotBlank(message = "模型编码不能为空")
    private String modelCode;
    private Long targetDataSourceId;
    private String targetTableName;
    private String status;
    private String remark;
    @Valid
    private List<CollectModelField> fields = new ArrayList<>();
}
