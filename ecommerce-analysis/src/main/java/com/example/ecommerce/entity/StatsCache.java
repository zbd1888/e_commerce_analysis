package com.example.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_stats_cache")
public class StatsCache {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String statKey;
    private Long statValue;
    private BigDecimal statPercent;
    private String statJson;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

