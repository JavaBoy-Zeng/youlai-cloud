package com.youlai.flowable.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.flowable.model.entity.AppBuilderForm;
import com.youlai.flowable.model.form.AppBuilderFormForm;
import com.youlai.flowable.model.query.AppBuilderFormPageQuery;
import com.youlai.flowable.model.vo.AppBuilderFormVO;

public interface AppBuilderFormService extends IService<AppBuilderForm> {

    Page<AppBuilderFormVO> getFormPage(AppBuilderFormPageQuery queryParams);

    AppBuilderFormVO getForm(Long id);

    AppBuilderFormVO getFormByKey(String formKey);

    AppBuilderFormVO saveForm(AppBuilderFormForm form);

    AppBuilderFormVO publishForm(Long id);

    boolean deleteForm(Long id);
}
