package com.example.ecommerce.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ecommerce.common.Result;
import com.example.ecommerce.entity.Rank;
import com.example.ecommerce.service.RankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "榜单管理")
@RestController
@RequestMapping("/api/rank")
@RequiredArgsConstructor
public class RankController {

    private final RankService rankService;

    @Operation(summary = "分页查询榜单")
    @GetMapping("/list")
    public Result<Page<Rank>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category) {
        Page<Rank> page = rankService.pageList(pageNum, pageSize, category);
        return Result.success(page);
    }

    @Operation(summary = "导入Excel数据")
    @PostMapping("/import")
    public Result<Void> importExcel(@RequestParam String filePath) {
        try {
            rankService.importFromExcel(filePath);
            return Result.success();
        } catch (Exception e) {
            return Result.error("导入失败: " + e.getMessage());
        }
    }

    @Operation(summary = "分类统计")
    @GetMapping("/stats/category")
    public Result<List<Map<String, Object>>> categoryStats() {
        return Result.success(rankService.getCategoryStats());
    }

    @Operation(summary = "热门榜单TOP")
    @GetMapping("/top")
    public Result<List<Rank>> topRanks(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(rankService.getTopRanks(limit));
    }

    @Operation(summary = "获取榜单总数")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(rankService.count());
    }
}
