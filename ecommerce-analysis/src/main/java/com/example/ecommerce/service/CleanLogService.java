package com.example.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ecommerce.entity.CleanLog;

import java.util.List;

/**
 * 清洗日志服务接口
 */
public interface CleanLogService extends IService<CleanLog> {
    
    /**
     * 根据批次ID获取清洗日志
     */
    CleanLog getByBatchId(String batchId);
    
    /**
     * 获取最近的清洗日志
     */
    List<CleanLog> getRecentLogs(int limit);
    
    /**
     * 获取运行中的清洗任务
     */
    List<CleanLog> getRunningTasks();
    
    /**
     * 创建新的清洗日志
     */
    CleanLog createLog(String fileName, String keyword, String operator);
    
    /**
     * 更新清洗日志状态
     */
    void updateLogStatus(String batchId, String status, Integer inserted, Integer updated,
                         Integer skipped, String errorMsg);
}

