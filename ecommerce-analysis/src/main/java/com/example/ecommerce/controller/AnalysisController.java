package com.example.ecommerce.controller;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "数据分析与预测")
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "地域分布数据")
    @GetMapping("/region-distribution")
    public Result<List<Map<String, Object>>> getRegionDistribution(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        return Result.success(analysisService.getRegionDistribution(keyword));
    }

    @Operation(summary = "店铺销量排行")
    @GetMapping("/store-ranking")
    public Result<List<Map<String, Object>>> getStoreRanking() {
        return Result.success(analysisService.getStoreRanking());
    }

    @Operation(summary = "热度榜单TOP20")
    @GetMapping("/rank-hotlist")
    public Result<List<Map<String, Object>>> getRankHotlist() {
        return Result.success(analysisService.getRankHotlist());
    }

    @Operation(summary = "店铺标签分布")
    @GetMapping("/shop-tag-distribution")
    public Result<List<Map<String, Object>>> getShopTagDistribution() {
        return Result.success(analysisService.getShopTagDistribution());
    }

    @Operation(summary = "价格-销量散点图数据")
    @GetMapping("/price-sale-scatter")
    public Result<List<Map<String, Object>>> getPriceSaleScatter() {
        return Result.success(analysisService.getPriceSaleScatter());
    }

    @Operation(summary = "市场供需分析")
    @GetMapping("/market-supply-demand")
    public Result<List<Map<String, Object>>> getMarketSupplyDemand() {
        return Result.success(analysisService.getMarketSupplyDemand());
    }

    @Operation(summary = "蓝海品类（高热度低竞争）")
    @GetMapping("/blue-ocean")
    public Result<List<Map<String, Object>>> getBlueOceanCategories() {
        return Result.success(analysisService.getBlueOceanCategories());
    }

    @Operation(summary = "红海品类（高竞争）")
    @GetMapping("/red-ocean")
    public Result<List<Map<String, Object>>> getRedOceanCategories() {
        return Result.success(analysisService.getRedOceanCategories());
    }

    @Operation(summary = "价格策略分析")
    @GetMapping("/price-strategy")
    public Result<List<Map<String, Object>>> getPriceStrategy() {
        return Result.success(analysisService.getPriceStrategy());
    }

    @Operation(summary = "各品类最优价格区间")
    @GetMapping("/optimal-price-range")
    public Result<List<Map<String, Object>>> getOptimalPriceRange() {
        return Result.success(analysisService.getOptimalPriceRange());
    }

    @Operation(summary = "爆款特征分析")
    @GetMapping("/hot-product-features")
    public Result<Map<String, Object>> getHotProductFeatures(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        return Result.success(analysisService.getHotProductFeatures(keyword));
    }

    @Operation(summary = "热门标题关键词")
    @GetMapping("/hot-title-keywords")
    public Result<List<Map<String, Object>>> getHotTitleKeywords(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        return Result.success(analysisService.getHotTitleKeywords(keyword));
    }

    @Operation(summary = "店铺竞争力分析")
    @GetMapping("/store-analysis")
    public Result<List<Map<String, Object>>> getStoreAnalysis() {
        return Result.success(analysisService.getStoreAnalysis());
    }

    @Operation(summary = "店龄与销量关系")
    @GetMapping("/store-age-sales")
    public Result<List<Map<String, Object>>> getStoreAgeSales() {
        return Result.success(analysisService.getStoreAgeSales());
    }

    @Operation(summary = "按品类获取店铺标签分布")
    @GetMapping("/shop-tag-by-category")
    public Result<List<Map<String, Object>>> getShopTagByCategory(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        return Result.success(analysisService.getShopTagByCategory(keyword));
    }

    @Operation(summary = "地域品类分析")
    @GetMapping("/region-category")
    public Result<List<Map<String, Object>>> getRegionCategoryAnalysis() {
        return Result.success(analysisService.getRegionCategoryAnalysis());
    }

    @Operation(summary = "品类深度分析")
    @GetMapping("/category-deep")
    public Result<List<Map<String, Object>>> getCategoryDeepAnalysis() {
        return Result.success(analysisService.getCategoryDeepAnalysis());
    }

    @Operation(summary = "品类潜力排行")
    @GetMapping("/category-potential")
    public Result<List<Map<String, Object>>> getCategoryPotentialRanking() {
        return Result.success(analysisService.getCategoryPotentialRanking());
    }

    @Operation(summary = "定价建议预测")
    @GetMapping("/predict/price")
    public Result<Map<String, Object>> predictPrice(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "medium") String expectedSaleLevel) {
        return Result.success(analysisService.predictPrice(keyword, expectedSaleLevel));
    }

    @Operation(summary = "销量预测")
    @GetMapping("/predict/sales")
    public Result<Map<String, Object>> predictSales(
            @RequestParam String keyword,
            @RequestParam Double price) {
        return Result.success(analysisService.predictSales(keyword, price));
    }

    @Operation(summary = "市场潜力评估")
    @GetMapping("/predict/potential")
    public Result<Map<String, Object>> evaluateMarketPotential(@RequestParam String keyword) {
        return Result.success(analysisService.evaluateMarketPotential(keyword));
    }

    @Operation(summary = "综合预测 - 根据商品信息预测销量和成功率")
    @GetMapping("/predict/comprehensive")
    public Result<Map<String, Object>> comprehensivePrediction(
            @RequestParam String keyword,
            @RequestParam Double price,
            @RequestParam(required = false, defaultValue = "") String location,
            @RequestParam(required = false, defaultValue = "") String shopTag,
            @RequestParam(required = false, defaultValue = "") String title) {
        return Result.success(analysisService.comprehensivePrediction(keyword, price, location, shopTag, title));
    }
}

