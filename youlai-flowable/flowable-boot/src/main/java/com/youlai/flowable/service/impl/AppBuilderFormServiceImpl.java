package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.flowable.mapper.AppBuilderFormMapper;
import com.youlai.flowable.mapper.AppBuilderModelMapper;
import com.youlai.flowable.mapper.WfInstanceMapper;
import com.youlai.flowable.mapper.WfModelMapper;
import com.youlai.flowable.model.entity.AppBuilderForm;
import com.youlai.flowable.model.entity.AppBuilderModel;
import com.youlai.flowable.model.entity.WfInstance;
import com.youlai.flowable.model.entity.WfModel;
import com.youlai.flowable.model.form.AppBuilderFormForm;
import com.youlai.flowable.model.query.AppBuilderFormPageQuery;
import com.youlai.flowable.model.vo.AppBuilderFormVO;
import com.youlai.flowable.service.AppBuilderFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppBuilderFormServiceImpl extends ServiceImpl<AppBuilderFormMapper, AppBuilderForm>
        implements AppBuilderFormService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final AppBuilderOperationLogService operationLogService;
    private final AppBuilderModelMapper appBuilderModelMapper;
    private final WfModelMapper wfModelMapper;
    private final WfInstanceMapper wfInstanceMapper;

    @Override
    public Page<AppBuilderFormVO> getFormPage(AppBuilderFormPageQuery queryParams) {
        Page<AppBuilderForm> page = this.page(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                new LambdaQueryWrapper<AppBuilderForm>()
                        .eq(queryParams.getAppId() != null, AppBuilderForm::getAppId, queryParams.getAppId())
                        .eq(queryParams.getModelId() != null, AppBuilderForm::getModelId, queryParams.getModelId())
                        .like(StrUtil.isNotBlank(queryParams.getFormKey()), AppBuilderForm::getFormKey, StrUtil.trim(queryParams.getFormKey()))
                        .like(StrUtil.isNotBlank(queryParams.getFormName()), AppBuilderForm::getFormName, StrUtil.trim(queryParams.getFormName()))
                        .eq(StrUtil.isNotBlank(queryParams.getStatus()), AppBuilderForm::getStatus, queryParams.getStatus())
                        .and(StrUtil.isNotBlank(queryParams.getKeywords()), wrapper -> wrapper
                                .like(AppBuilderForm::getFormName, queryParams.getKeywords())
                                .or()
                                .like(AppBuilderForm::getFormKey, queryParams.getKeywords()))
                        .orderByDesc(AppBuilderForm::getUpdateTime, AppBuilderForm::getId));
        Page<AppBuilderFormVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Override
    public AppBuilderFormVO getForm(Long id) {
        AppBuilderForm form = this.getById(id);
        Assert.notNull(form, "表单不存在");
        return toVO(form);
    }

    @Override
    public AppBuilderFormVO getFormByKey(String formKey) {
        Assert.isTrue(StrUtil.isNotBlank(formKey), "表单标识不能为空");
        AppBuilderForm form = this.getOne(new LambdaQueryWrapper<AppBuilderForm>()
                .eq(AppBuilderForm::getFormKey, StrUtil.trim(formKey)));
        Assert.notNull(form, "表单不存在");
        return toVO(form);
    }

    @Override
    public AppBuilderFormVO saveForm(AppBuilderFormForm form) {
        Long id = form.getId();
        String formKey = StrUtil.trim(form.getFormKey());
        long count = this.count(new LambdaQueryWrapper<AppBuilderForm>()
                .ne(id != null, AppBuilderForm::getId, id)
                .eq(AppBuilderForm::getFormKey, formKey));
        Assert.isTrue(count == 0, "表单标识已存在");

        AppBuilderForm entity = new AppBuilderForm();
        if (id != null) {
            AppBuilderForm current = this.getById(id);
            Assert.notNull(current, "表单不存在");
            assertFormNotReferenced(current.getFormKey(), "表单已被引用，请先解绑后再修改");
            if (!StrUtil.equals(current.getFormKey(), formKey)) {
                assertFormNotReferenced(formKey, "表单标识已被引用，请先解绑后再修改");
            }
        }
        BeanUtils.copyProperties(form, entity);
        entity.setFormKey(formKey);
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
        if (StrUtil.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        boolean created = entity.getId() == null;
        this.saveOrUpdate(entity);
        operationLogService.record(entity.getAppId(), "FORM", created ? "CREATE" : "UPDATE", entity, "保存表单");
        return toVO(entity);
    }

    @Override
    public AppBuilderFormVO publishForm(Long id) {
        AppBuilderForm form = this.getById(id);
        Assert.notNull(form, "表单不存在");
        form.setStatus(STATUS_PUBLISHED);
        this.updateById(form);
        operationLogService.record(form.getAppId(), "FORM", "PUBLISH", form, "发布表单");
        return toVO(form);
    }

    @Override
    public boolean deleteForm(Long id) {
        AppBuilderForm form = this.getById(id);
        Assert.notNull(form, "表单不存在");
        assertFormNotReferenced(form.getFormKey(), "表单已被引用，请先解绑后再删除");
        boolean removed = this.removeById(id);
        if (removed) {
            operationLogService.record(form.getAppId(), "FORM", "DELETE", form, "删除表单");
        }
        return removed;
    }

    private void assertFormNotReferenced(String formKey, String message) {
        if (StrUtil.isBlank(formKey)) {
            return;
        }
        String key = StrUtil.trim(formKey);
        long appModelCount = appBuilderModelMapper.selectCount(new LambdaQueryWrapper<AppBuilderModel>()
                .eq(AppBuilderModel::getFormKey, key));
        long workflowModelCount = wfModelMapper.selectCount(new LambdaQueryWrapper<WfModel>()
                .eq(WfModel::getFormKey, key));
        long workflowInstanceCount = wfInstanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getFormKey, key));
        Assert.isTrue(appModelCount + workflowModelCount + workflowInstanceCount == 0, message);
    }

    private AppBuilderFormVO toVO(AppBuilderForm entity) {
        AppBuilderFormVO vo = new AppBuilderFormVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
