package com.youlai.flowable.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.flowable.model.entity.WfCategory;
import com.youlai.flowable.model.form.WfCategoryForm;
import com.youlai.flowable.model.vo.WfCategoryVO;

import java.util.List;

public interface WfCategoryService extends IService<WfCategory> {

    List<WfCategoryVO> listCategories();

    boolean saveCategory(WfCategoryForm form);
}
