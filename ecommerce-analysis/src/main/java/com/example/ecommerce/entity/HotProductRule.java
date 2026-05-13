package com.example.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 爆品规则配置实体类
 */
@Data
@TableName("tb_hot_product_rule")
public class HotProductRule {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 规则名称
     */
    private String ruleName;
    
    /**
     * 适用品类（空表示全局规则）
     */
    private String keyword;
    
    /**
     * 最低销量
     */
    private Integer minSales;
    
    /**
     * 最高销量
     */
    private Integer maxSales;
    
    /**
     * 最低价格
     */
    private BigDecimal minPrice;
    
    /**
     * 最高价格
     */
    private BigDecimal maxPrice;
    
    /**
     * 店铺标签（逗号分隔）
     */
    private String shopTags;

    /**
     * 匹配模式：AND（所有条件都满足）/ OR（满足任意条件）
     * 默认为 AND
     */
    private String matchMode;

    /**
     * 状态：1启用，0禁用
     */
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

