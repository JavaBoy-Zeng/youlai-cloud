package com.youlai.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youlai.decision.model.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
