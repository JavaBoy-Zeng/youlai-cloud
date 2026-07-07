package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.flowable.converter.WorkflowConverter;
import com.youlai.flowable.mapper.WfCategoryMapper;
import com.youlai.flowable.model.entity.WfCategory;
import com.youlai.flowable.model.form.WfCategoryForm;
import com.youlai.flowable.model.vo.WfCategoryVO;
import com.youlai.flowable.service.WfCategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WfCategoryServiceImpl extends ServiceImpl<WfCategoryMapper, WfCategory> implements WfCategoryService {

    @Override
    public List<WfCategoryVO> listCategories() {
        return this.list(new LambdaQueryWrapper<WfCategory>().orderByAsc(WfCategory::getSort, WfCategory::getId))
                .stream()
                .map(WorkflowConverter::toCategoryVO)
                .toList();
    }

    @Override
    public boolean saveCategory(WfCategoryForm form) {
        Long id = form.getId();
        long count = this.count(new LambdaQueryWrapper<WfCategory>()
                .ne(id != null, WfCategory::getId, id)
                .and(wrapper -> wrapper.eq(WfCategory::getCode, form.getCode())
                        .or()
                        .eq(WfCategory::getName, form.getName())));
        Assert.isTrue(count == 0, "流程分类名称或编码已存在");

        WfCategory category = new WfCategory();
        BeanUtils.copyProperties(form, category);
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        if (category.getSort() == null) {
            category.setSort(0);
        }
        category.setCode(StrUtil.trim(category.getCode()));
        return this.saveOrUpdate(category);
    }
}
