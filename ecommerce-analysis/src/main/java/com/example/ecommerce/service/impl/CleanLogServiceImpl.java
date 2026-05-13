package com.example.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ecommerce.entity.CleanLog;
import com.example.ecommerce.mapper.CleanLogMapper;
import com.example.ecommerce.service.CleanLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 清洗日志服务实现类
 */
@Service
public class CleanLogServiceImpl extends ServiceImpl<CleanLogMapper, CleanLog> implements CleanLogService {
    
    @Override
    public CleanLog getByBatchId(String batchId) {
        return baseMapper.getByBatchId(batchId);
    }
    
    @Override
    public List<CleanLog> getRecentLogs(int limit) {
        return baseMapper.getRecentLogs(limit);
    }
    
    @Override
    public List<CleanLog> getRunningTasks() {
        return baseMapper.getRunningTasks();
    }
    
    @Override
    public CleanLog createLog(String fileName, String keyword, String operator) {
        CleanLog log = new CleanLog();
        log.setBatchId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        log.setFileName(fileName);
        log.setKeyword(keyword);
        log.setOperator(operator);
        log.setStatus("RUNNING");
        log.setStartedAt(LocalDateTime.now());
        log.setTotalCount(0);
        log.setInsertedCount(0);
        log.setUpdatedCount(0);
        log.setSkippedCount(0);
        save(log);
        return log;
    }

    @Override
    public void updateLogStatus(String batchId, String status, Integer inserted, Integer updated,
                                Integer skipped, String errorMsg) {
        CleanLog log = getByBatchId(batchId);
        if (log != null) {
            log.setStatus(status);
            log.setInsertedCount(inserted);
            log.setUpdatedCount(updated);
            log.setSkippedCount(skipped);
            log.setTotalCount(inserted + updated + skipped);
            log.setErrorMsg(errorMsg);
            log.setFinishedAt(LocalDateTime.now());
            updateById(log);
        }
    }
}

