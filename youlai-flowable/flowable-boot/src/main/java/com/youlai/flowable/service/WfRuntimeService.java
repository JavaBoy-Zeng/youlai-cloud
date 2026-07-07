package com.youlai.flowable.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.flowable.model.entity.WfInstance;
import com.youlai.flowable.model.form.StartProcessForm;
import com.youlai.flowable.model.query.WfInstancePageQuery;
import com.youlai.flowable.model.vo.ProcessDiagramVO;
import com.youlai.flowable.model.vo.WfInstanceVO;

public interface WfRuntimeService extends IService<WfInstance> {

    WfInstanceVO startProcess(StartProcessForm form);

    Page<WfInstanceVO> getInstancePage(WfInstancePageQuery queryParams);

    WfInstanceVO getInstanceDetail(String processInstanceId);

    ProcessDiagramVO getDiagram(String processInstanceId);

    boolean revoke(String processInstanceId, String reason);

    boolean terminate(String processInstanceId, String reason);

    void markRejected(String processInstanceId);

    void refreshInstanceStatus(String processInstanceId);
}
