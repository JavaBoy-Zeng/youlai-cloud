package com.youlai.flowable.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.flowable.model.entity.WfTaskRecord;
import com.youlai.flowable.model.form.TaskApproveForm;
import com.youlai.flowable.model.query.WfTaskPageQuery;
import com.youlai.flowable.model.vo.WfTaskVO;

public interface WfTaskService extends IService<WfTaskRecord> {

    Page<WfTaskVO> getTodoPage(WfTaskPageQuery queryParams);

    Page<WfTaskVO> getDonePage(WfTaskPageQuery queryParams);

    boolean complete(String taskId, TaskApproveForm form);

    boolean reject(String taskId, TaskApproveForm form);

    boolean transfer(String taskId, TaskApproveForm form);

    boolean delegate(String taskId, TaskApproveForm form);

    boolean addSign(String taskId, TaskApproveForm form);

    boolean claim(String taskId);

    boolean unclaim(String taskId);
}
