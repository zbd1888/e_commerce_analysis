package com.example.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据清洗日志实体
 */
@Data
@TableName("tb_clean_log")
public class CleanLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 批次ID */
    private String batchId;
    
    /** 处理的文件名 */
    private String fileName;
    
    /** 关键词 */
    private String keyword;
    
    /** 总记录数 */
    private Integer totalCount;
    
    /** 新增数量 */
    private Integer insertedCount;
    
    /** 更新数量 */
    private Integer updatedCount;
    
    /** 跳过数量（间隔不足） */
    private Integer skippedCount;

    /** 状态：RUNNING/SUCCESS/FAILED */
    private String status;
    
    /** 错误信息 */
    private String errorMsg;
    
    /** 操作人（管理员） */
    private String operator;
    
    /** 开始时间 */
    private LocalDateTime startedAt;
    
    /** 完成时间 */
    private LocalDateTime finishedAt;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

