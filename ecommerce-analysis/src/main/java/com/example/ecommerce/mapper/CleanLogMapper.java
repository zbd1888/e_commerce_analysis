package com.example.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ecommerce.entity.CleanLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CleanLogMapper extends BaseMapper<CleanLog> {
    
    /**
     * 根据批次ID获取清洗日志
     */
    @Select("SELECT * FROM tb_clean_log WHERE batch_id = #{batchId}")
    CleanLog getByBatchId(@Param("batchId") String batchId);
    
    /**
     * 获取最近的清洗日志
     */
    @Select("SELECT * FROM tb_clean_log ORDER BY created_at DESC LIMIT #{limit}")
    List<CleanLog> getRecentLogs(@Param("limit") int limit);
    
    /**
     * 获取运行中的清洗任务
     */
    @Select("SELECT * FROM tb_clean_log WHERE status = 'RUNNING'")
    List<CleanLog> getRunningTasks();
}

