package com.youlai.flowable.converter;

import com.youlai.flowable.model.entity.WfCategory;
import com.youlai.flowable.model.entity.WfInstance;
import com.youlai.flowable.model.entity.WfModel;
import com.youlai.flowable.model.entity.WfTaskRecord;
import com.youlai.flowable.model.vo.WfCategoryVO;
import com.youlai.flowable.model.vo.WfInstanceVO;
import com.youlai.flowable.model.vo.WfModelVO;
import com.youlai.flowable.model.vo.WfTaskRecordVO;
import org.springframework.beans.BeanUtils;

public class WorkflowConverter {

    private WorkflowConverter() {
    }

    public static WfCategoryVO toCategoryVO(WfCategory source) {
        WfCategoryVO target = new WfCategoryVO();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    public static WfModelVO toModelVO(WfModel source) {
        WfModelVO target = new WfModelVO();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    public static WfInstanceVO toInstanceVO(WfInstance source) {
        WfInstanceVO target = new WfInstanceVO();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    public static WfTaskRecordVO toTaskRecordVO(WfTaskRecord source) {
        WfTaskRecordVO target = new WfTaskRecordVO();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
