package com.example.ecommerce.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ecommerce.common.Result;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "商品管理")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "分页查询商品")
    @GetMapping("/list")
    public Result<Page<Product>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Long minSales,
            @RequestParam(required = false) Long maxSales) {
        Page<Product> page = productService.pageList(pageNum, pageSize, keyword, store, province, minPrice, maxPrice, minSales, maxSales);
        return Result.success(page);
    }

    @Operation(summary = "获取商品详情")
    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "导入Excel数据")
    @PostMapping("/import")
    public Result<Void> importExcel(@RequestParam String filePath) {
        try {
            productService.importFromExcel(filePath);
            return Result.success();
        } catch (Exception e) {
            return Result.error("导入失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取关键词列表")
    @GetMapping("/keywords")
    public Result<List<String>> getKeywords() {
        return Result.success(productService.getKeywordList());
    }

    @Operation(summary = "获取发货地列表")
    @GetMapping("/locations")
    public Result<List<String>> getLocations() {
        return Result.success(productService.getLocationList());
    }

    @Operation(summary = "获取店铺列表")
    @GetMapping("/stores")
    public Result<List<String>> getStores() {
        return Result.success(productService.getStoreList());
    }

    @Operation(summary = "关键词统计")
    @GetMapping("/stats/keyword")
    public Result<List<Map<String, Object>>> keywordStats() {
        return Result.success(productService.getKeywordStats());
    }

    @Operation(summary = "省份分布统计")
    @GetMapping("/stats/province")
    public Result<List<Map<String, Object>>> provinceStats(
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.getProvinceStats(keyword));
    }

    @Operation(summary = "店铺排行（按商品数量）")
    @GetMapping("/stats/store")
    public Result<List<Map<String, Object>>> storeStats(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.getStoreStats(limit, keyword));
    }

    @Operation(summary = "店铺深度分析（按销量汇总）")
    @GetMapping("/stats/store-detail")
    public Result<List<Map<String, Object>>> storeDetailStats(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.getStoreDetailStats(limit, keyword));
    }

    @Operation(summary = "价格区间统计")
    @GetMapping("/stats/price")
    public Result<List<Map<String, Object>>> priceStats(
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.getPriceRangeStats(keyword));
    }

    @Operation(summary = "概览统计")
    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> overviewStats(
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.getOverviewStats(keyword));
    }
}
