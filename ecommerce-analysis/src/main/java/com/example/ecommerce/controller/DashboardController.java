package com.example.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ecommerce.common.Result;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.service.AnalysisService;
import com.example.ecommerce.service.ProductService;
import com.example.ecommerce.service.RankService;
import com.example.ecommerce.service.StatsCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Tag(name = "数据大屏")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProductService productService;
    private final StatsCacheService statsCacheService;
    private final AnalysisService analysisService;
    private final RankService rankService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "大屏核心统计数据（从缓存读取，毫秒级响应）")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(statsCacheService.getUserDashboardStats());
    }

    @Operation(summary = "热销商品TOP N（实时查询，数据量小）")
    @GetMapping("/top-products")
    public Result<List<Product>> getTopProducts(@RequestParam(defaultValue = "10") Integer limit) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Product::getSaleValue).last("LIMIT " + limit);
        return Result.success(productService.list(wrapper));
    }

    @Operation(summary = "品类销量统计（从缓存读取，毫秒级响应）")
    @GetMapping("/category-sales")
    public Result<List<Map<String, Object>>> getCategorySales() {
        try {
            String json = statsCacheService.getCategorySalesJson();
            if (json != null && !json.isEmpty()) {
                List<Map<String, Object>> result = objectMapper.readValue(json,
                        new TypeReference<List<Map<String, Object>>>() {});
                return Result.success(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.success(new ArrayList<>());
    }

    @Operation(summary = "价格区间分布（从缓存读取，毫秒级响应）")
    @GetMapping("/price-distribution")
    public Result<List<Map<String, Object>>> getPriceDistribution() {
        try {
            String json = statsCacheService.getPriceDistributionJson();
            if (json != null && !json.isEmpty()) {
                List<Map<String, Object>> result = objectMapper.readValue(json,
                        new TypeReference<List<Map<String, Object>>>() {});
                return Result.success(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.success(new ArrayList<>());
    }

    @Operation(summary = "聚合数据接口（并行查询，一次性返回所有大屏数据）")
    @GetMapping("/aggregated")
    public Result<Map<String, Object>> getAggregatedData(@RequestParam(defaultValue = "10") Integer limit) {
        Map<String, Object> result = new ConcurrentHashMap<>();

        try {
            // 使用 CompletableFuture 并行执行所有查询
            CompletableFuture<Void> statsFuture = CompletableFuture.runAsync(() ->
                result.put("stats", statsCacheService.getUserDashboardStats())
            );

            CompletableFuture<Void> productsFuture = CompletableFuture.runAsync(() -> {
                LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
                wrapper.orderByDesc(Product::getSaleValue).last("LIMIT " + limit);
                result.put("topProducts", productService.list(wrapper));
            });

            CompletableFuture<Void> categorySalesFuture = CompletableFuture.runAsync(() -> {
                try {
                    String json = statsCacheService.getCategorySalesJson();
                    result.put("categorySales", (json != null && !json.isEmpty())
                        ? objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {})
                        : new ArrayList<>());
                } catch (Exception e) { result.put("categorySales", new ArrayList<>()); }
            });

            CompletableFuture<Void> priceFuture = CompletableFuture.runAsync(() -> {
                try {
                    String json = statsCacheService.getPriceDistributionJson();
                    result.put("priceDistribution", (json != null && !json.isEmpty())
                        ? objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {})
                        : new ArrayList<>());
                } catch (Exception e) { result.put("priceDistribution", new ArrayList<>()); }
            });

            CompletableFuture<Void> rankFuture = CompletableFuture.runAsync(() ->
                result.put("rankHotlist", analysisService.getRankHotlist())
            );

            CompletableFuture<Void> regionFuture = CompletableFuture.runAsync(() ->
                result.put("regionDistribution", analysisService.getRegionDistribution(""))
            );

            CompletableFuture<Void> storeFuture = CompletableFuture.runAsync(() ->
                result.put("storeRanking", analysisService.getStoreRanking())
            );

            CompletableFuture<Void> shopTagFuture = CompletableFuture.runAsync(() ->
                result.put("shopTagDistribution", analysisService.getShopTagDistribution())
            );

            CompletableFuture<Void> rankCountFuture = CompletableFuture.runAsync(() ->
                result.put("rankCount", rankService.count())
            );

            // 等待所有任务完成
            CompletableFuture.allOf(statsFuture, productsFuture, categorySalesFuture, priceFuture,
                    rankFuture, regionFuture, storeFuture, shopTagFuture, rankCountFuture).join();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.success(result);
    }
}

