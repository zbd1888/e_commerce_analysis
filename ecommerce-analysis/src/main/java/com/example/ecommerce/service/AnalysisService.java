package com.example.ecommerce.service;

import java.util.List;
import java.util.Map;

/**
 * 数据分析与预测服务
 */
public interface AnalysisService {

    /**
     * 获取地域分布数据
     * @param keyword 品类关键词，为空则返回全部
     */
    List<Map<String, Object>> getRegionDistribution(String keyword);
    
    /**
     * 获取店铺销量排行
     */
    List<Map<String, Object>> getStoreRanking();
    
    /**
     * 获取热度榜单数据
     */
    List<Map<String, Object>> getRankHotlist();
    
    /**
     * 获取店铺标签分布
     */
    List<Map<String, Object>> getShopTagDistribution();
    
    /**
     * 获取价格-销量散点图数据
     */
    List<Map<String, Object>> getPriceSaleScatter();

    /**
     * 获取市场供需分析
     */
    List<Map<String, Object>> getMarketSupplyDemand();
    
    /**
     * 获取蓝海品类（高热度低竞争）
     */
    List<Map<String, Object>> getBlueOceanCategories();
    
    /**
     * 获取红海品类（高竞争）
     */
    List<Map<String, Object>> getRedOceanCategories();

    /**
     * 获取价格策略分析
     */
    List<Map<String, Object>> getPriceStrategy();
    
    /**
     * 获取各品类最优价格区间
     */
    List<Map<String, Object>> getOptimalPriceRange();

    /**
     * 获取爆款特征分析
     * @param keyword 品类关键词，为空则返回全局数据
     */
    Map<String, Object> getHotProductFeatures(String keyword);
    
    /**
     * 获取热门标题关键词
     * @param keyword 品类关键词，为空则返回全局数据
     */
    List<Map<String, Object>> getHotTitleKeywords(String keyword);

    /**
     * 获取热门标题关键词（无参数版本，返回全局数据）
     */
    default List<Map<String, Object>> getHotTitleKeywords() {
        return getHotTitleKeywords(null);
    }

    /**
     * 获取店铺竞争力分析
     */
    List<Map<String, Object>> getStoreAnalysis();

    /**
     * 获取店龄与销量关系
     */
    List<Map<String, Object>> getStoreAgeSales();

    /**
     * 按品类获取店铺标签分布
     * @param keyword 品类关键词，为空则返回全部
     */
    List<Map<String, Object>> getShopTagByCategory(String keyword);

    /**
     * 获取地域品类分析
     */
    List<Map<String, Object>> getRegionCategoryAnalysis();

    /**
     * 获取品类深度分析
     */
    List<Map<String, Object>> getCategoryDeepAnalysis();

    /**
     * 定价建议预测
     * @param keyword 品类关键词
     * @param expectedSaleLevel 期望销量等级(low/medium/high)
     */
    Map<String, Object> predictPrice(String keyword, String expectedSaleLevel);

    /**
     * 销量预测
     * @param keyword 品类关键词
     * @param price 定价
     */
    Map<String, Object> predictSales(String keyword, Double price);

    /**
     * 市场潜力评估
     * @param keyword 品类关键词
     */
    Map<String, Object> evaluateMarketPotential(String keyword);

    /**
     * 获取所有品类潜力排行
     */
    List<Map<String, Object>> getCategoryPotentialRanking();

    /**
     * 综合预测 - 根据商品信息预测销量和成功率
     * @param keyword 品类
     * @param price 价格
     * @param location 发货地
     * @param shopTag 店铺标签
     * @param title 商品标题
     */
    Map<String, Object> comprehensivePrediction(String keyword, Double price, String location, String shopTag, String title);

    /**
     * 刷新所有分析数据缓存
     */
    void refreshAllAnalysisCache();

    /**
     * 刷新爆品相关缓存（规则变更时调用）
     */
    void refreshHotProductCache();
}

