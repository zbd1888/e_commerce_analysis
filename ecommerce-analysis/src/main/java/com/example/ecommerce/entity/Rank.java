package com.example.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_rank")
public class Rank {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String category;
    private String rankName;
    private String hotDesc;
    private Integer hotValue;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
