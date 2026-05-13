package com.example.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ecommerce.entity.StatsCache;
import java.math.BigDecimal;
import java.util.Map;

public interface StatsCacheService extends IService<StatsCache> {
    
    /**
     * 获取整数型统计值
     */
    Long getValue(String key);
    
    /**
     * 获取百分比型统计值
     */
    BigDecimal getPercent(String key);
    
    /**
     * 获取JSON型统计值
     */
    String getJson(String key);
    
    /**
     * 更新整数型统计值
     */
    void updateValue(String key, Long value);
    
    /**
     * 更新百分比型统计值
     */
    void updatePercent(String key, BigDecimal percent);
    
    /**
     * 更新JSON型统计值
     */
    void updateJson(String key, String json);
    
    /**
     * 刷新所有统计数据（从数据库重新计算）
     */
    void refreshAllStats();
    
    /**
     * 获取大屏统计数据（从缓存读取）
     */
    Map<String, Object> getAdminStats();
    
    /**
     * 获取数据质量统计（从缓存读取）
     */
    Map<String, Object> getDataQuality();

    /**
     * 获取用户大屏统计数据（从缓存读取）
     */
    Map<String, Object> getUserDashboardStats();

    /**
     * 获取品类销量统计（从缓存读取）
     */
    String getCategorySalesJson();

    /**
     * 获取价格分布统计（从缓存读取）
     */
    String getPriceDistributionJson();
}

