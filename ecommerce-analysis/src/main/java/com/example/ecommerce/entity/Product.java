package com.example.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productId;
    private String picUrl;
    private String title;
    private BigDecimal price;
    private String saleNum;
    private Integer saleValue;
    private String store;
    private String shopUrl;
    private String keyword;
    private String province;
    private String city;
    private String location;
    private String shopTag;
    private BigDecimal couponPrice;
    private Integer isCleaned;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
