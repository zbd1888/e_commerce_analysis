package com.example.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ecommerce.entity.HotProductRule;
import com.example.ecommerce.mapper.HotProductRuleMapper;
import com.example.ecommerce.service.HotProductRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 爆品规则配置服务实现类
 */
@Service
@RequiredArgsConstructor
public class HotProductRuleServiceImpl extends ServiceImpl<HotProductRuleMapper, HotProductRule> implements HotProductRuleService {
    
    @Override
    public List<HotProductRule> getAllRules() {
        return list(new LambdaQueryWrapper<HotProductRule>()
                .orderByDesc(HotProductRule::getCreatedAt));
    }
    
    @Override
    public List<HotProductRule> getEnabledRules() {
        return baseMapper.selectEnabledRules();
    }
    
    @Override
    public List<HotProductRule> getRulesByKeyword(String keyword) {
        return baseMapper.selectRulesByKeyword(keyword);
    }
    
    @Override
    public boolean saveOrUpdateRule(HotProductRule rule) {
        if (rule.getId() == null) {
            // 新增时设置默认状态
            if (rule.getStatus() == null) {
                rule.setStatus(1);
            }
            if (rule.getMinSales() == null) {
                rule.setMinSales(0);
            }
            return save(rule);
        } else {
            return updateById(rule);
        }
    }
    
    @Override
    public boolean toggleStatus(Long id) {
        HotProductRule rule = getById(id);
        if (rule != null) {
            rule.setStatus(rule.getStatus() == 1 ? 0 : 1);
            return updateById(rule);
        }
        return false;
    }
}

