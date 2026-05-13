package com.example.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ecommerce.entity.HotProductRule;

import java.util.List;

/**
 * 爆品规则配置服务接口
 */
public interface HotProductRuleService extends IService<HotProductRule> {
    
    /**
     * 获取所有规则列表
     */
    List<HotProductRule> getAllRules();
    
    /**
     * 获取启用的规则列表
     */
    List<HotProductRule> getEnabledRules();
    
    /**
     * 根据品类获取适用的规则
     */
    List<HotProductRule> getRulesByKeyword(String keyword);
    
    /**
     * 保存或更新规则
     */
    boolean saveOrUpdateRule(HotProductRule rule);
    
    /**
     * 切换规则状态
     */
    boolean toggleStatus(Long id);
}

