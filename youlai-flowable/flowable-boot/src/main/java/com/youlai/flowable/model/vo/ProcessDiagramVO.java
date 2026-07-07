package com.youlai.flowable.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProcessDiagramVO {

    private String bpmnXml;

    private List<String> activeActivityIds;

    private List<String> finishedActivityIds;
}
