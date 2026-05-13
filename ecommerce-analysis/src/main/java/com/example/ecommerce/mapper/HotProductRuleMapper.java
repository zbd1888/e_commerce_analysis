package com.example.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ecommerce.entity.HotProductRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 爆品规则配置Mapper
 */
@Mapper
public interface HotProductRuleMapper extends BaseMapper<HotProductRule> {
    
    /**
     * 获取启用的规则列表
     */
    @Select("SELECT * FROM tb_hot_product_rule WHERE status = 1 ORDER BY created_at DESC")
    List<HotProductRule> selectEnabledRules();
    
    /**
     * 根据品类获取适用的规则（包括全局规则）
     */
    @Select("SELECT * FROM tb_hot_product_rule WHERE status = 1 AND (keyword IS NULL OR keyword = '' OR keyword = #{keyword}) ORDER BY keyword DESC")
    List<HotProductRule> selectRulesByKeyword(@Param("keyword") String keyword);
}

