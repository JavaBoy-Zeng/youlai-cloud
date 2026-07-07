package com.youlai.flowable.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.flowable.model.entity.WfModel;
import com.youlai.flowable.model.form.WfModelForm;
import com.youlai.flowable.model.query.WfModelPageQuery;
import com.youlai.flowable.model.vo.WfModelVO;

public interface WfModelService extends IService<WfModel> {

    Page<WfModelVO> getModelPage(WfModelPageQuery queryParams);

    WfModelVO getModel(Long id);

    boolean saveModel(WfModelForm form);

    WfModelVO publishModel(Long id);

    boolean updateDefinitionState(Long id, boolean suspended);

    boolean deleteModel(Long id);

    String exportBpmnXml(Long id);
}
