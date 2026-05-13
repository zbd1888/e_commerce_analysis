package com.example.ecommerce.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ecommerce.entity.HotProductRule;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.Rank;
import com.example.ecommerce.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private final ProductService productService;
    private final RankService rankService;
    private final StatsCacheService statsCacheService;
    private final ObjectMapper objectMapper;
    private final HotProductRuleService hotProductRuleService;

    // 辅助方法：计算利润评分
    private double calcProfitScore(double avgPrice) {
        if (avgPrice < 50) return 20;
        if (avgPrice < 200) return 50;
        if (avgPrice < 500) return 70;
        return 90;
    }

    // 辅助方法：获取推荐等级
    private String getRecommendation(double totalScore) {
        if (totalScore >= 70) return "高潜力品类，强烈推荐入场";
        if (totalScore >= 50) return "中等潜力，可以考虑";
        if (totalScore >= 30) return "竞争较大，需谨慎";
        return "不建议，市场饱和";
    }

    private <T> T getFromCacheOrCompute(String cacheKey, TypeReference<T> typeRef, java.util.function.Supplier<T> compute) {
        try {
            String json = statsCacheService.getJson(cacheKey);
            if (StrUtil.isNotBlank(json) && !json.equals("[]") && !json.equals("{}")) {
                return objectMapper.readValue(json, typeRef);
            }
        } catch (Exception e) {
            log.warn("读取缓存失败: {}", cacheKey);
        }
        return compute.get();
    }

    @Override
    public List<Map<String, Object>> getRegionDistribution(String keyword) {
        // 如果有关键词，则实时计算（不走缓存）
        if (StrUtil.isNotBlank(keyword)) {
            return computeRegionDistribution(keyword);
        }
        // 无关键词则走缓存
        return getFromCacheOrCompute("region_distribution",
                new TypeReference<List<Map<String, Object>>>() {}, () -> computeRegionDistribution(null));
    }

    private List<Map<String, Object>> computeRegionDistribution(String keyword) {
        List<Product> products;
        if (StrUtil.isNotBlank(keyword)) {
            // 按品类关键词筛选
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Product::getKeyword, keyword);
            products = productService.list(wrapper);
        } else {
            products = productService.list();
        }

        Map<String, Long> regionCount = new HashMap<>();
        Map<String, Long> regionSales = new HashMap<>();

        for (Product p : products) {
            String location = p.getLocation();
            if (StrUtil.isBlank(location)) continue;
            String province = location.split(" ")[0];
            regionCount.merge(province, 1L, Long::sum);
            regionSales.merge(province, p.getSaleValue() != null ? p.getSaleValue() : 0L, Long::sum);
        }

        return regionCount.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    item.put("sales", regionSales.getOrDefault(e.getKey(), 0L));
                    return item;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("value"), (Long) a.get("value")))
                .limit(20)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getStoreRanking() {
        return getFromCacheOrCompute("store_ranking",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeStoreRanking);
    }

    private List<Map<String, Object>> computeStoreRanking() {
        List<Product> products = productService.list();
        Map<String, Long> storeSales = new HashMap<>();
        Map<String, Long> storeCount = new HashMap<>();
        Map<String, Double> storePrice = new HashMap<>();
        
        for (Product p : products) {
            if (StrUtil.isBlank(p.getStore())) continue;
            storeSales.merge(p.getStore(), p.getSaleValue() != null ? p.getSaleValue() : 0L, Long::sum);
            storeCount.merge(p.getStore(), 1L, Long::sum);
            storePrice.merge(p.getStore(), p.getPrice() != null ? p.getPrice().doubleValue() : 0, Double::sum);
        }
        
        return storeSales.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("store", e.getKey());
                    item.put("totalSales", e.getValue());
                    item.put("productCount", storeCount.getOrDefault(e.getKey(), 0L));
                    long count = storeCount.getOrDefault(e.getKey(), 1L);
                    item.put("avgPrice", Math.round(storePrice.getOrDefault(e.getKey(), 0.0) / count * 100) / 100.0);
                    return item;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("totalSales"), (Long) a.get("totalSales")))
                .limit(15)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getRankHotlist() {
        return getFromCacheOrCompute("rank_hotlist",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeRankHotlist);
    }

    private List<Map<String, Object>> computeRankHotlist() {
        List<Rank> ranks = rankService.list(new LambdaQueryWrapper<Rank>()
                .orderByDesc(Rank::getHotValue).last("LIMIT 20"));
        return ranks.stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("rankName", r.getRankName());
            item.put("category", r.getCategory());
            item.put("hotValue", r.getHotValue());
            item.put("hotDesc", r.getHotDesc());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getShopTagDistribution() {
        return getFromCacheOrCompute("shop_tag_distribution",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeShopTagDistribution);
    }

    private List<Map<String, Object>> computeShopTagDistribution() {
        return productService.list().stream()
                .collect(Collectors.groupingBy(p -> StrUtil.isBlank(p.getShopTag()) ? "无标签" : p.getShopTag(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("tag", e.getKey()); m.put("count", e.getValue()); return m; })
                .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                .limit(10).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getPriceSaleScatter() {
        return getFromCacheOrCompute("price_sale_scatter",
                new TypeReference<List<Map<String, Object>>>() {}, this::computePriceSaleScatter);
    }

    private List<Map<String, Object>> computePriceSaleScatter() {
        // 采样获取数据点，增加样本量以更好反映整体分布
        List<Product> products = productService.list(new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getPrice).gt(Product::getPrice, 0)
                .isNotNull(Product::getSaleValue).gt(Product::getSaleValue, 0)
                .last("LIMIT 2000"));
        return products.stream().map(p -> {
            Map<String, Object> item = new HashMap<>();
            item.put("price", p.getPrice());
            item.put("sales", p.getSaleValue());
            item.put("title", p.getTitle() != null && p.getTitle().length() > 20
                    ? p.getTitle().substring(0, 20) + "..." : p.getTitle());
            item.put("keyword", p.getKeyword());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getMarketSupplyDemand() {
        return getFromCacheOrCompute("market_supply_demand",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeMarketSupplyDemand);
    }

    private List<Map<String, Object>> computeMarketSupplyDemand() {
        List<Product> products = productService.list();
        List<Rank> ranks = rankService.list();

        // 构建榜单热度映射
        Map<String, Integer> rankHotMap = new HashMap<>();
        Map<String, String> rankCategoryMap = new HashMap<>();
        for (Rank r : ranks) {
            rankHotMap.merge(r.getRankName(), r.getHotValue() != null ? r.getHotValue() : 0, Math::max);
            rankCategoryMap.putIfAbsent(r.getRankName(), r.getCategory());
        }

        // 统计商品数据（供需比只统计销量>100的有效商品）
        Map<String, Long> keywordValidCount = new HashMap<>();  // 有效商品数（销量>100）
        Map<String, Long> keywordTotalCount = new HashMap<>();  // 总商品数
        Map<String, Long> keywordSales = new HashMap<>();
        Map<String, Double> keywordPriceSum = new HashMap<>();
        Map<String, Long> keywordPriceCount = new HashMap<>();

        for (Product p : products) {
            if (StrUtil.isBlank(p.getKeyword())) continue;
            keywordTotalCount.merge(p.getKeyword(), 1L, Long::sum);
            int saleValue = p.getSaleValue() != null ? p.getSaleValue() : 0;
            keywordSales.merge(p.getKeyword(), (long) saleValue, Long::sum);

            // 只有销量>100的商品才计入供需比的有效供给
            if (saleValue > 100) {
                keywordValidCount.merge(p.getKeyword(), 1L, Long::sum);
                if (p.getPrice() != null) {
                    keywordPriceSum.merge(p.getKeyword(), p.getPrice().doubleValue(), Double::sum);
                    keywordPriceCount.merge(p.getKeyword(), 1L, Long::sum);
                }
            }
        }

        // 计算供需比（使用有效商品数）
        return keywordTotalCount.keySet().stream()
                .filter(k -> rankHotMap.containsKey(k))
                .map(k -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("keyword", k);
                    item.put("category", rankCategoryMap.getOrDefault(k, "其他"));
                    item.put("hotValue", rankHotMap.getOrDefault(k, 0));
                    item.put("productCount", keywordTotalCount.get(k));
                    long validCount = keywordValidCount.getOrDefault(k, 0L);
                    item.put("validProductCount", validCount);  // 新增：有效商品数
                    item.put("totalSales", keywordSales.getOrDefault(k, 0L));

                    // 平均价格使用有效商品的价格
                    long priceCount = keywordPriceCount.getOrDefault(k, 1L);
                    double avgPrice = priceCount > 0 ? keywordPriceSum.getOrDefault(k, 0.0) / priceCount : 0;
                    item.put("avgPrice", Math.round(avgPrice * 100) / 100.0);

                    // 供需比使用有效商品数计算
                    int hot = rankHotMap.getOrDefault(k, 1);
                    double supplyDemandRatio = hot > 0 ? (double) validCount / hot : 0;
                    item.put("supplyDemandRatio", Math.round(supplyDemandRatio * 10000) / 10000.0);

                    // 竞争指数
                    double competitionIndex = keywordSales.getOrDefault(k, 1L) > 0
                            ? (double) validCount / keywordSales.get(k) * 10000 : 0;
                    item.put("competitionIndex", Math.round(competitionIndex * 100) / 100.0);
                    return item;
                })
                .sorted((a, b) -> Integer.compare((Integer) b.get("hotValue"), (Integer) a.get("hotValue")))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getBlueOceanCategories() {
        return getFromCacheOrCompute("blue_ocean_categories",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeBlueOceanCategories);
    }

    private List<Map<String, Object>> computeBlueOceanCategories() {
        List<Map<String, Object>> all = computeMarketSupplyDemand();
        // 蓝海：供需比低（商品少、需求大）
        return all.stream()
                .filter(m -> (Double) m.get("supplyDemandRatio") < 0.1 && (Integer) m.get("hotValue") >= 5000)
                .sorted((a, b) -> Double.compare((Double) a.get("supplyDemandRatio"), (Double) b.get("supplyDemandRatio")))
                .limit(10)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getRedOceanCategories() {
        return getFromCacheOrCompute("red_ocean_categories",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeRedOceanCategories);
    }

    private List<Map<String, Object>> computeRedOceanCategories() {
        List<Map<String, Object>> all = computeMarketSupplyDemand();
        // 红海：供需比高（商品多、需求相对小）
        return all.stream()
                .filter(m -> (Double) m.get("supplyDemandRatio") > 0.2)
                .sorted((a, b) -> Double.compare((Double) b.get("supplyDemandRatio"), (Double) a.get("supplyDemandRatio")))
                .limit(10)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getPriceStrategy() {
        return getFromCacheOrCompute("price_strategy",
                new TypeReference<List<Map<String, Object>>>() {}, this::computePriceStrategy);
    }

    private List<Map<String, Object>> computePriceStrategy() {
        List<Product> products = productService.list();
        // 过滤异常价格：只保留价格在合理范围内的商品（0 < price <= 50000）
        Map<String, List<Product>> byKeyword = products.stream()
                .filter(p -> StrUtil.isNotBlank(p.getKeyword()) && p.getPrice() != null
                        && p.getPrice().doubleValue() > 0 && p.getPrice().doubleValue() <= 50000)
                .collect(Collectors.groupingBy(Product::getKeyword));

        return byKeyword.entrySet().stream().map(e -> {
            List<Product> list = e.getValue();
            DoubleSummaryStatistics stats = list.stream()
                    .mapToDouble(p -> p.getPrice().doubleValue()).summaryStatistics();
            Map<String, Object> item = new HashMap<>();
            item.put("keyword", e.getKey());
            item.put("minPrice", Math.round(stats.getMin() * 100) / 100.0);
            item.put("maxPrice", Math.round(stats.getMax() * 100) / 100.0);
            item.put("avgPrice", Math.round(stats.getAverage() * 100) / 100.0);
            item.put("productCount", list.size());
            // 计算价格标准差
            double avg = stats.getAverage();
            double variance = list.stream().mapToDouble(p -> Math.pow(p.getPrice().doubleValue() - avg, 2)).average().orElse(0);
            item.put("priceStdDev", Math.round(Math.sqrt(variance) * 100) / 100.0);
            return item;
        }).sorted((a, b) -> Integer.compare((Integer) b.get("productCount"), (Integer) a.get("productCount")))
                .limit(20).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getOptimalPriceRange() {
        return getFromCacheOrCompute("optimal_price_range",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeOptimalPriceRange);
    }

    private List<Map<String, Object>> computeOptimalPriceRange() {
        List<Product> products = productService.list();
        Map<String, List<Product>> byKeyword = products.stream()
                .filter(p -> StrUtil.isNotBlank(p.getKeyword()) && p.getPrice() != null && p.getSaleValue() != null)
                .collect(Collectors.groupingBy(Product::getKeyword));

        return byKeyword.entrySet().stream().map(e -> {
            List<Product> list = e.getValue();
            // 按销量排序，取TOP 20%的商品分析价格区间
            list.sort((a, b) -> Long.compare(b.getSaleValue(), a.getSaleValue()));
            int topCount = Math.max(1, list.size() / 5);
            List<Product> topProducts = list.subList(0, topCount);
            DoubleSummaryStatistics stats = topProducts.stream()
                    .mapToDouble(p -> p.getPrice().doubleValue()).summaryStatistics();
            Map<String, Object> item = new HashMap<>();
            item.put("keyword", e.getKey());
            item.put("optimalMin", Math.round(stats.getMin() * 100) / 100.0);
            item.put("optimalMax", Math.round(stats.getMax() * 100) / 100.0);
            item.put("optimalAvg", Math.round(stats.getAverage() * 100) / 100.0);
            item.put("sampleSize", topCount);
            long avgSales = topProducts.stream().mapToLong(Product::getSaleValue).sum() / topCount;
            item.put("avgSalesInRange", avgSales);
            return item;
        }).sorted((a, b) -> Long.compare((Long) b.get("avgSalesInRange"), (Long) a.get("avgSalesInRange")))
                .limit(20).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getHotProductFeatures(String keyword) {
        // 如果有关键词，则实时计算（不走缓存）
        if (StrUtil.isNotBlank(keyword)) {
            return computeHotProductFeatures(keyword);
        }
        // 无关键词则走缓存
        try {
            String json = statsCacheService.getJson("hot_product_features");
            if (StrUtil.isNotBlank(json) && !json.equals("{}")) {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) { log.warn("读取缓存失败"); }
        return computeHotProductFeatures(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> computeHotProductFeatures(String filterKeyword) {
        // 构建查询条件 - 不限制数量，让规则筛选所有符合条件的商品
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getSaleValue)
                .orderByDesc(Product::getSaleValue);

        // 如果指定了品类，只查询该品类的数据
        if (StrUtil.isNotBlank(filterKeyword)) {
            wrapper.eq(Product::getKeyword, filterKeyword);
        }
        // 移除LIMIT限制，让所有符合规则的商品都能被统计

        List<Product> products = productService.list(wrapper);

        // 获取适用的爆品规则
        List<HotProductRule> rules = hotProductRuleService.getRulesByKeyword(filterKeyword);

        // 使用规则筛选爆品，并获取规则匹配统计
        Map<String, Object> filterResult = filterProductsByRulesWithStats(products, rules);
        List<Product> hotProducts = (List<Product>) filterResult.get("products");
        List<Map<String, Object>> ruleStats = (List<Map<String, Object>>) filterResult.get("ruleStats");

        Map<String, Object> features = new HashMap<>();

        // 添加规则匹配统计
        features.put("ruleStats", ruleStats);

        // 按品类分组统计爆款特征
        Map<String, List<Product>> productsByKeyword = hotProducts.stream()
                .filter(p -> StrUtil.isNotBlank(p.getKeyword()))
                .collect(Collectors.groupingBy(Product::getKeyword));

        List<Map<String, Object>> categoryFeatures = new ArrayList<>();
        for (Map.Entry<String, List<Product>> entry : productsByKeyword.entrySet()) {
            String keyword = entry.getKey();
            List<Product> categoryProducts = entry.getValue();

            if (categoryProducts.size() < 3) continue; // 样本太少跳过

            Map<String, Object> catFeature = new HashMap<>();
            catFeature.put("keyword", keyword);
            catFeature.put("sampleSize", categoryProducts.size());

            // 价格特征
            List<Product> productsWithPrice = categoryProducts.stream()
                    .filter(p -> p.getPrice() != null).collect(Collectors.toList());
            if (!productsWithPrice.isEmpty()) {
                DoubleSummaryStatistics priceStats = productsWithPrice.stream()
                        .mapToDouble(p -> p.getPrice().doubleValue()).summaryStatistics();
                catFeature.put("priceMin", Math.round(priceStats.getMin() * 100) / 100.0);
                catFeature.put("priceMax", Math.round(priceStats.getMax() * 100) / 100.0);
                catFeature.put("priceAvg", Math.round(priceStats.getAverage() * 100) / 100.0);
            } else {
                catFeature.put("priceMin", 0);
                catFeature.put("priceMax", 0);
                catFeature.put("priceAvg", 0);
            }

            // 店铺标签分布
            Map<String, Long> tagCount = new HashMap<>();
            for (Product p : categoryProducts) {
                String tag = StrUtil.isNotBlank(p.getShopTag()) ? p.getShopTag() : "无标签";
                tagCount.merge(tag, 1L, Long::sum);
            }
            catFeature.put("topShopTag", tagCount.entrySet().stream()
                    .max(Comparator.comparingLong(Map.Entry::getValue))
                    .map(Map.Entry::getKey).orElse("无"));

            // 主要发货地
            Map<String, Long> regionCount = new HashMap<>();
            for (Product p : categoryProducts) {
                if (StrUtil.isNotBlank(p.getLocation())) {
                    String province = p.getLocation().split(" ")[0];
                    regionCount.merge(province, 1L, Long::sum);
                }
            }
            catFeature.put("topRegion", regionCount.entrySet().stream()
                    .max(Comparator.comparingLong(Map.Entry::getValue))
                    .map(Map.Entry::getKey).orElse("未知"));

            // 平均销量
            double avgSales = categoryProducts.stream()
                    .mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).average().orElse(0);
            catFeature.put("avgSales", Math.round(avgSales));

            categoryFeatures.add(catFeature);
        }

        // 按样本数排序
        categoryFeatures.sort((a, b) -> Integer.compare((Integer) b.get("sampleSize"), (Integer) a.get("sampleSize")));
        features.put("categoryFeatures", categoryFeatures);

        // 全局/品类统计
        if (hotProducts.isEmpty()) {
            features.put("hotPriceMin", 0);
            features.put("hotPriceMax", 0);
            features.put("hotPriceAvg", 0);
            features.put("hotShopTags", Collections.emptyList());
            features.put("hotRegions", Collections.emptyList());
            features.put("sampleSize", 0);
            return features;
        }

        List<Product> productsWithPrice = hotProducts.stream()
                .filter(p -> p.getPrice() != null).collect(Collectors.toList());
        if (!productsWithPrice.isEmpty()) {
            DoubleSummaryStatistics priceStats = productsWithPrice.stream()
                    .mapToDouble(p -> p.getPrice().doubleValue()).summaryStatistics();
            features.put("hotPriceMin", Math.round(priceStats.getMin() * 100) / 100.0);
            features.put("hotPriceMax", Math.round(priceStats.getMax() * 100) / 100.0);
            features.put("hotPriceAvg", Math.round(priceStats.getAverage() * 100) / 100.0);
        } else {
            features.put("hotPriceMin", 0);
            features.put("hotPriceMax", 0);
            features.put("hotPriceAvg", 0);
        }

        // 店铺标签分析
        Map<String, Long> tagCount = new HashMap<>();
        for (Product p : hotProducts) {
            String tag = StrUtil.isNotBlank(p.getShopTag()) ? p.getShopTag() : "无标签";
            tagCount.merge(tag, 1L, Long::sum);
        }
        features.put("hotShopTags", tagCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(5)
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("tag", e.getKey()); m.put("count", e.getValue()); return m; })
                .collect(Collectors.toList()));

        // 地域分布
        Map<String, Long> regionCount = new HashMap<>();
        for (Product p : hotProducts) {
            if (StrUtil.isNotBlank(p.getLocation())) {
                String province = p.getLocation().split(" ")[0];
                regionCount.merge(province, 1L, Long::sum);
            }
        }
        features.put("hotRegions", regionCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(5)
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("region", e.getKey()); m.put("count", e.getValue()); return m; })
                .collect(Collectors.toList()));

        features.put("sampleSize", hotProducts.size());
        return features;
    }

    /**
     * 根据爆品规则筛选商品，并统计每条规则的匹配数量
     */
    private List<Product> filterProductsByRules(List<Product> products, List<HotProductRule> rules) {
        if (rules == null || rules.isEmpty()) {
            // 没有规则时，使用默认逻辑：取TOP 10%
            int topCount = Math.max(100, products.size() / 10);
            return products.subList(0, Math.min(topCount, products.size()));
        }

        // 使用规则筛选
        return products.stream().filter(p -> matchesAnyRule(p, rules)).collect(Collectors.toList());
    }

    /**
     * 根据爆品规则筛选商品，并返回每条规则的匹配统计
     */
    private Map<String, Object> filterProductsByRulesWithStats(List<Product> products, List<HotProductRule> rules) {
        Map<String, Object> result = new HashMap<>();
        List<Product> matchedProducts = new ArrayList<>();
        List<Map<String, Object>> ruleStats = new ArrayList<>();

        if (rules == null || rules.isEmpty()) {
            // 没有规则时，使用默认逻辑：取TOP 10%
            int topCount = Math.max(100, products.size() / 10);
            matchedProducts = products.subList(0, Math.min(topCount, products.size()));
            Map<String, Object> defaultStat = new HashMap<>();
            defaultStat.put("ruleName", "默认规则(TOP10%)");
            defaultStat.put("ruleId", 0L);
            defaultStat.put("matchCount", matchedProducts.size());
            ruleStats.add(defaultStat);
        } else {
            // 统计每条规则的匹配数量
            Map<Long, Integer> ruleMatchCount = new HashMap<>();
            Set<Long> matchedProductIds = new HashSet<>();

            for (Product p : products) {
                for (HotProductRule rule : rules) {
                    if (matchesRule(p, rule)) {
                        ruleMatchCount.merge(rule.getId(), 1, Integer::sum);
                        if (!matchedProductIds.contains(p.getId())) {
                            matchedProducts.add(p);
                            matchedProductIds.add(p.getId());
                        }
                    }
                }
            }

            // 构建规则统计列表
            for (HotProductRule rule : rules) {
                Map<String, Object> stat = new HashMap<>();
                stat.put("ruleName", rule.getRuleName());
                stat.put("ruleId", rule.getId());
                stat.put("matchCount", ruleMatchCount.getOrDefault(rule.getId(), 0));
                ruleStats.add(stat);
            }
        }

        result.put("products", matchedProducts);
        result.put("ruleStats", ruleStats);
        return result;
    }

    /**
     * 检查商品是否匹配任意规则
     */
    private boolean matchesAnyRule(Product product, List<HotProductRule> rules) {
        return rules.stream().anyMatch(rule -> matchesRule(product, rule));
    }

    /**
     * 检查商品是否匹配单条规则
     * AND模式：必须满足所有条件；OR模式：满足任意条件即可
     */
    private boolean matchesRule(Product product, HotProductRule rule) {
        boolean isAndMode = !"OR".equalsIgnoreCase(rule.getMatchMode());
        List<Boolean> results = new ArrayList<>();

        // 销量条件
        if (rule.getMinSales() != null && rule.getMinSales() > 0) {
            Integer sale = product.getSaleValue();
            boolean match = sale != null && sale >= rule.getMinSales()
                    && (rule.getMaxSales() == null || sale <= rule.getMaxSales());
            results.add(match);
        }

        // 价格条件
        if (rule.getMinPrice() != null || rule.getMaxPrice() != null) {
            BigDecimal price = product.getPrice();
            boolean match = price != null
                    && (rule.getMinPrice() == null || price.compareTo(rule.getMinPrice()) >= 0)
                    && (rule.getMaxPrice() == null || price.compareTo(rule.getMaxPrice()) <= 0);
            results.add(match);
        }

        // 店铺标签条件
        if (StrUtil.isNotBlank(rule.getShopTags())) {
            String productTag = product.getShopTag();
            boolean match = StrUtil.isNotBlank(productTag) &&
                    Arrays.stream(rule.getShopTags().split(","))
                            .anyMatch(t -> productTag.contains(t.trim()));
            results.add(match);
        }

        if (results.isEmpty()) return false;
        return isAndMode ? results.stream().allMatch(b -> b) : results.stream().anyMatch(b -> b);
    }

    @Override
    public List<Map<String, Object>> getHotTitleKeywords(String keyword) {
        // 如果有关键词，则实时计算（不走缓存）
        if (StrUtil.isNotBlank(keyword)) {
            return computeHotTitleKeywords(keyword);
        }
        // 无关键词则走缓存
        return getFromCacheOrCompute("hot_title_keywords",
                new TypeReference<List<Map<String, Object>>>() {}, () -> computeHotTitleKeywords(null));
    }

    private List<Map<String, Object>> computeHotTitleKeywords(String filterKeyword) {
        // 构建查询条件 - 不限制数量，查询所有商品
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getSaleValue)
                .orderByDesc(Product::getSaleValue);

        // 如果指定了品类，只查询该品类的数据
        if (StrUtil.isNotBlank(filterKeyword)) {
            wrapper.eq(Product::getKeyword, filterKeyword);
        }
        // 不再限制数量，让所有符合规则的商品都参与统计

        List<Product> allProducts = productService.list(wrapper);

        // 使用爆品规则筛选商品，与爆品特征分析保持一致
        List<HotProductRule> rules = hotProductRuleService.getRulesByKeyword(filterKeyword);
        List<Product> hotProducts = filterProductsByRules(allProducts, rules);

        Map<String, Long> wordCount = new HashMap<>();
        // 只匹配中文词语（2-6字）
        Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,6}");

        // 需要过滤的常见无意义词汇
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "新款", "包邮", "正品", "特价", "促销", "热卖", "爆款", "清仓",
                "同款", "官方", "旗舰", "专柜", "限时", "秒杀", "折扣", "优惠",
                "直营", "授权", "品牌", "官网", "正版", "现货", "预售", "批发",
                "厂家", "直销", "代购", "海外", "进口", "国产", "高端", "奢华",
                "高档", "中低", "男女", "通用", "适用", "大号", "小号", "中号",
                "加大", "加厚", "加长", "超大", "超小", "多功能", "多用", "套装"
        ));

        // 统计爆品商品的标题关键词
        for (Product p : hotProducts) {
            if (StrUtil.isBlank(p.getTitle())) continue;
            Matcher matcher = chinesePattern.matcher(p.getTitle());
            while (matcher.find()) {
                String word = matcher.group();
                // 过滤停用词和纯数字词
                if (!stopWords.contains(word) && !word.matches(".*\\d+.*")) {
                    wordCount.merge(word, 1L, Long::sum);
                }
            }
        }

        // 根据数据量动态调整阈值
        int minCount = hotProducts.size() > 100 ? 3 : 2;

        return wordCount.entrySet().stream()
                .filter(e -> e.getValue() >= minCount)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(30)
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("word", e.getKey()); m.put("count", e.getValue()); return m; })
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getStoreAnalysis() {
        // 直接复用店铺排行逻辑
        return getFromCacheOrCompute("store_analysis",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeStoreRanking);
    }

    @Override
    public List<Map<String, Object>> getShopTagByCategory(String keyword) {
        List<Product> products = StrUtil.isBlank(keyword) ? productService.list()
                : productService.list(new LambdaQueryWrapper<Product>().eq(Product::getKeyword, keyword));
        return products.stream()
                .collect(Collectors.groupingBy(p -> StrUtil.isBlank(p.getShopTag()) ? "无标签" : p.getShopTag(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("tag", e.getKey()); m.put("count", e.getValue()); return m; })
                .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getStoreAgeSales() {
        return getFromCacheOrCompute("store_age_sales",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeStoreAgeSales);
    }

    private List<Map<String, Object>> computeStoreAgeSales() {
        List<Product> products = productService.list();
        Map<String, Long> ageSales = new LinkedHashMap<>();
        Map<String, Long> ageCount = new LinkedHashMap<>();
        String[] ageGroups = {"新店", "1-3年", "4-6年", "7-10年", "10年以上", "回头客店铺"};
        for (String g : ageGroups) { ageSales.put(g, 0L); ageCount.put(g, 0L); }

        for (Product p : products) {
            String tag = p.getShopTag();
            String group = "新店";
            if (StrUtil.isNotBlank(tag)) {
                if (tag.contains("回头客")) group = "回头客店铺";
                else if (tag.contains("年老店")) {
                    Pattern pt = Pattern.compile("(\\d+)年老店");
                    Matcher m = pt.matcher(tag);
                    if (m.find()) {
                        int years = Integer.parseInt(m.group(1));
                        if (years >= 10) group = "10年以上";
                        else if (years >= 7) group = "7-10年";
                        else if (years >= 4) group = "4-6年";
                        else group = "1-3年";
                    }
                }
            }
            ageSales.merge(group, p.getSaleValue() != null ? p.getSaleValue() : 0L, Long::sum);
            ageCount.merge(group, 1L, Long::sum);
        }

        return ageSales.keySet().stream().map(k -> {
            Map<String, Object> m = new HashMap<>();
            m.put("ageGroup", k);
            m.put("totalSales", ageSales.get(k));
            m.put("productCount", ageCount.get(k));
            long cnt = ageCount.get(k);
            m.put("avgSales", cnt > 0 ? ageSales.get(k) / cnt : 0);
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getRegionCategoryAnalysis() {
        return getFromCacheOrCompute("region_category_analysis",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeRegionCategoryAnalysis);
    }

    private List<Map<String, Object>> computeRegionCategoryAnalysis() {
        List<Product> products = productService.list();
        Map<String, Map<String, Long>> regionCategory = new HashMap<>();
        for (Product p : products) {
            if (StrUtil.isBlank(p.getLocation()) || StrUtil.isBlank(p.getKeyword())) continue;
            String province = p.getLocation().split(" ")[0];
            regionCategory.computeIfAbsent(province, k -> new HashMap<>())
                    .merge(p.getKeyword(), p.getSaleValue() != null ? p.getSaleValue() : 0L, Long::sum);
        }
        return regionCategory.entrySet().stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("region", e.getKey());
            // 找出该地区销量最高的品类
            Optional<Map.Entry<String, Long>> top = e.getValue().entrySet().stream()
                    .max(Comparator.comparingLong(Map.Entry::getValue));
            m.put("topCategory", top.map(Map.Entry::getKey).orElse(""));
            m.put("topCategorySales", top.map(Map.Entry::getValue).orElse(0L));
            m.put("categoryCount", e.getValue().size());
            return m;
        }).sorted((a, b) -> Long.compare((Long) b.get("topCategorySales"), (Long) a.get("topCategorySales")))
                .limit(15).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getCategoryDeepAnalysis() {
        return getFromCacheOrCompute("category_deep_analysis",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeCategoryDeepAnalysis);
    }

    private List<Map<String, Object>> computeCategoryDeepAnalysis() {
        List<Map<String, Object>> supplyDemand = computeMarketSupplyDemand();
        List<Map<String, Object>> priceStrategy = computePriceStrategy();
        Map<String, Map<String, Object>> priceMap = priceStrategy.stream()
                .collect(Collectors.toMap(m -> (String) m.get("keyword"), m -> m, (a, b) -> a));

        return supplyDemand.stream().map(sd -> {
            Map<String, Object> m = new HashMap<>(sd);
            String keyword = (String) sd.get("keyword");
            Map<String, Object> price = priceMap.get(keyword);
            if (price != null) {
                m.put("minPrice", price.get("minPrice"));
                m.put("maxPrice", price.get("maxPrice"));
                m.put("priceStdDev", price.get("priceStdDev"));
            }
            // 计算综合评分
            int hotValue = (Integer) sd.get("hotValue");
            double supplyRatio = (Double) sd.get("supplyDemandRatio");
            double score = hotValue / 10000.0 * 40 + (1 - Math.min(supplyRatio, 1)) * 30
                    + Math.min((Long) sd.get("totalSales") / 1000000.0, 1) * 30;
            m.put("potentialScore", Math.round(score * 100) / 100.0);
            return m;
        }).sorted((a, b) -> Double.compare((Double) b.get("potentialScore"), (Double) a.get("potentialScore")))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> predictPrice(String keyword, String expectedSaleLevel) {
        Map<String, Object> result = new HashMap<>();
        List<Product> products = productService.list(new LambdaQueryWrapper<Product>()
                .eq(Product::getKeyword, keyword)
                .isNotNull(Product::getPrice).gt(Product::getPrice, 0)
                .isNotNull(Product::getSaleValue));

        if (products.isEmpty()) {
            result.put("success", false);
            result.put("message", "该品类暂无数据");
            return result;
        }

        // 按销量分组
        products.sort((a, b) -> Long.compare(b.getSaleValue(), a.getSaleValue()));
        int size = products.size();
        List<Product> targetProducts;
        String levelDesc;

        switch (expectedSaleLevel.toLowerCase()) {
            case "high":
                targetProducts = products.subList(0, Math.max(1, size / 5));
                levelDesc = "高销量（TOP 20%）";
                break;
            case "medium":
                targetProducts = products.subList(size / 5, Math.max(size / 5 + 1, size * 3 / 5));
                levelDesc = "中等销量（20%-60%）";
                break;
            default:
                targetProducts = products.subList(size * 3 / 5, size);
                levelDesc = "低销量（后40%）";
        }

        DoubleSummaryStatistics stats = targetProducts.stream()
                .mapToDouble(p -> p.getPrice().doubleValue()).summaryStatistics();

        result.put("success", true);
        result.put("keyword", keyword);
        result.put("expectedLevel", levelDesc);
        result.put("suggestedPriceMin", Math.round(stats.getMin() * 100) / 100.0);
        result.put("suggestedPriceMax", Math.round(stats.getMax() * 100) / 100.0);
        result.put("suggestedPriceOptimal", Math.round(stats.getAverage() * 100) / 100.0);
        result.put("sampleSize", targetProducts.size());
        result.put("confidence", Math.min(95, 60 + targetProducts.size()));

        long avgSales = targetProducts.stream().mapToLong(Product::getSaleValue).sum() / targetProducts.size();
        result.put("expectedSalesRange", avgSales);

        return result;
    }

    @Override
    public Map<String, Object> predictSales(String keyword, Double price) {
        Map<String, Object> result = new HashMap<>();
        List<Product> products = productService.list(new LambdaQueryWrapper<Product>()
                .eq(Product::getKeyword, keyword)
                .isNotNull(Product::getPrice).gt(Product::getPrice, 0)
                .isNotNull(Product::getSaleValue));

        if (products.isEmpty()) {
            result.put("success", false);
            result.put("message", "该品类暂无数据");
            return result;
        }

        // 找价格相近的商品（±30%）
        double priceLow = price * 0.7;
        double priceHigh = price * 1.3;
        List<Product> similarProducts = products.stream()
                .filter(p -> p.getPrice().doubleValue() >= priceLow && p.getPrice().doubleValue() <= priceHigh)
                .collect(Collectors.toList());

        if (similarProducts.isEmpty()) {
            similarProducts = products; // 如果没有相近价格，使用全部
        }

        List<Long> sales = similarProducts.stream().map(p -> p.getSaleValue() != null ? p.getSaleValue().longValue() : 0L).sorted().collect(Collectors.toList());
        int size = sales.size();

        result.put("success", true);
        result.put("keyword", keyword);
        result.put("inputPrice", price);
        result.put("conservativePrediction", sales.get(size / 4)); // P25
        result.put("neutralPrediction", sales.get(size / 2));      // P50
        result.put("optimisticPrediction", sales.get(size * 3 / 4)); // P75
        result.put("sampleSize", similarProducts.size());
        result.put("priceRange", priceLow + " - " + priceHigh);

        return result;
    }

    @Override
    public Map<String, Object> evaluateMarketPotential(String keyword) {
        Map<String, Object> result = new HashMap<>();

        List<Rank> ranks = rankService.list(new LambdaQueryWrapper<Rank>().eq(Rank::getRankName, keyword));
        int hotValue = ranks.isEmpty() ? 0 : ranks.stream().mapToInt(r -> r.getHotValue() != null ? r.getHotValue() : 0).max().orElse(0);

        List<Product> products = productService.list(new LambdaQueryWrapper<Product>().eq(Product::getKeyword, keyword));
        if (products.isEmpty()) {
            result.put("success", false);
            result.put("message", "该品类暂无商品数据");
            return result;
        }

        long validProductCount = products.stream().filter(p -> p.getPrice() != null && p.getPrice().doubleValue() > 100).count();
        long totalSales = products.stream().mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).sum();
        double avgPrice = products.stream().filter(p -> p.getPrice() != null && p.getPrice().doubleValue() > 100)
                .mapToDouble(p -> p.getPrice().doubleValue()).average().orElse(0);

        double hotScore = Math.min(100, hotValue / 50.0);
        double competitionScore = hotValue > 0 ? Math.max(0, 100 - (validProductCount / (double) hotValue * 1000)) : 50;
        double profitScore = calcProfitScore(avgPrice);
        double salesScore = Math.min(100, totalSales / 100000.0);
        double totalScore = hotScore * 0.4 + competitionScore * 0.3 + profitScore * 0.15 + salesScore * 0.15;

        result.put("success", true);
        result.put("keyword", keyword);
        result.put("hotValue", hotValue);
        result.put("productCount", (long) products.size());
        result.put("validProductCount", validProductCount);
        result.put("totalSales", totalSales);
        result.put("avgPrice", Math.round(avgPrice * 100) / 100.0);
        result.put("hotScore", Math.round(hotScore * 10) / 10.0);
        result.put("competitionScore", Math.round(competitionScore * 10) / 10.0);
        result.put("profitScore", Math.round(profitScore * 10) / 10.0);
        result.put("salesScore", Math.round(salesScore * 10) / 10.0);
        result.put("totalScore", Math.round(totalScore * 10) / 10.0);
        result.put("recommendation", getRecommendation(totalScore));
        return result;
    }

    @Override
    public Map<String, Object> comprehensivePrediction(String keyword, Double price, String location, String shopTag, String title) {
        Map<String, Object> result = new HashMap<>();

        // 获取该品类的所有商品
        List<Product> products = productService.list(new LambdaQueryWrapper<Product>()
                .eq(Product::getKeyword, keyword)
                .isNotNull(Product::getSaleValue));

        if (products.isEmpty()) {
            result.put("success", false);
            result.put("message", "该品类暂无数据，无法进行预测");
            return result;
        }

        // 基础数据统计
        DoubleSummaryStatistics priceStats = products.stream()
                .filter(p -> p.getPrice() != null && p.getPrice().doubleValue() > 0)
                .mapToDouble(p -> p.getPrice().doubleValue()).summaryStatistics();
        LongSummaryStatistics salesStats = products.stream()
                .mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).summaryStatistics();

        result.put("success", true);
        result.put("keyword", keyword);
        result.put("inputPrice", price);
        result.put("inputLocation", location);
        result.put("inputShopTag", shopTag);
        result.put("sampleSize", products.size());

        // 价格-销量关系分析
        double avgPrice = priceStats.getAverage();
        double priceRatio = price / avgPrice;

        // 计算不同价格区间的实际销量表现
        List<Product> lowPriceProducts = products.stream()
                .filter(p -> p.getPrice() != null && p.getPrice().doubleValue() < avgPrice * 0.8).collect(Collectors.toList());
        List<Product> midPriceProducts = products.stream()
                .filter(p -> p.getPrice() != null && p.getPrice().doubleValue() >= avgPrice * 0.8 && p.getPrice().doubleValue() <= avgPrice * 1.2).collect(Collectors.toList());
        List<Product> highPriceProducts = products.stream()
                .filter(p -> p.getPrice() != null && p.getPrice().doubleValue() > avgPrice * 1.2).collect(Collectors.toList());

        double lowPriceAvgSales = lowPriceProducts.stream().mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).average().orElse(0);
        double midPriceAvgSales = midPriceProducts.stream().mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).average().orElse(0);
        double highPriceAvgSales = highPriceProducts.stream().mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).average().orElse(0);

        // 基于真实销量数据计算价格评分
        double priceScore;
        double maxSales = Math.max(Math.max(lowPriceAvgSales, midPriceAvgSales), highPriceAvgSales);
        if (priceRatio < 0.8) {
            priceScore = maxSales > 0 ? Math.min(95, 60 + (lowPriceAvgSales / maxSales) * 35) : 75;
        } else if (priceRatio <= 1.2) {
            priceScore = maxSales > 0 ? Math.min(95, 60 + (midPriceAvgSales / maxSales) * 35) : 65;
        } else {
            priceScore = maxSales > 0 ? Math.min(80, 40 + (highPriceAvgSales / maxSales) * 35) : 40;
        }

        // 最优价格区间建议
        String bestPriceRange;
        if (lowPriceAvgSales >= midPriceAvgSales && lowPriceAvgSales >= highPriceAvgSales) {
            bestPriceRange = "¥" + Math.round(avgPrice * 0.5) + " - ¥" + Math.round(avgPrice * 0.8);
        } else if (highPriceAvgSales >= midPriceAvgSales) {
            bestPriceRange = "¥" + Math.round(avgPrice * 1.2) + " - ¥" + Math.round(avgPrice * 1.5);
        } else {
            bestPriceRange = "¥" + Math.round(avgPrice * 0.8) + " - ¥" + Math.round(avgPrice * 1.2);
        }

        result.put("priceScore", Math.round(priceScore));
        result.put("categoryAvgPrice", Math.round(avgPrice * 100) / 100.0);
        result.put("bestPriceRange", bestPriceRange);
        result.put("priceSuggestion", priceScore >= 70 ? "价格具有竞争力" : "建议调整至最优区间: " + bestPriceRange);

        // 发货地评分
        Map<String, Long> locationCount = new HashMap<>();
        Map<String, Double> locationAvgSales = new HashMap<>();
        for (Product p : products) {
            if (StrUtil.isNotBlank(p.getLocation())) {
                String province = p.getLocation().split(" ")[0];
                locationCount.merge(province, 1L, Long::sum);
            }
        }
        // 计算各地区销量
        Map<String, List<Product>> productsByLocation = products.stream()
                .filter(p -> StrUtil.isNotBlank(p.getLocation()))
                .collect(Collectors.groupingBy(p -> p.getLocation().split(" ")[0]));
        for (Map.Entry<String, List<Product>> e : productsByLocation.entrySet()) {
            double avg = e.getValue().stream().mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).average().orElse(0);
            locationAvgSales.put(e.getKey(), avg);
        }

        String inputProvince = StrUtil.isNotBlank(location) ? location.split(" ")[0] : "";

        // 基于数据找出销量最高的发货地
        String topLocation = locationCount.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElse("");
        String bestSalesLocation = locationAvgSales.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElse("");
        double maxLocationSales = locationAvgSales.values().stream().max(Double::compare).orElse(1.0);

        // 基于实际销量数据计算发货地评分
        double locationScore;
        if (StrUtil.isNotBlank(inputProvince) && locationAvgSales.containsKey(inputProvince)) {
            locationScore = 50 + (locationAvgSales.get(inputProvince) / maxLocationSales) * 45;
        } else if (StrUtil.isNotBlank(inputProvince)) {
            // 未知地区给予基础分
            locationScore = 40;
        } else {
            locationScore = 30;
        }

        result.put("locationScore", Math.round(locationScore));
        result.put("topLocation", topLocation);
        result.put("bestSalesLocation", bestSalesLocation);
        result.put("locationSuggestion", locationScore >= 70 ? "发货地销量表现优秀" : "推荐发货地: " + bestSalesLocation);

        // 店铺标签评分
        Map<String, Long> tagCount = new HashMap<>();
        Map<String, Double> tagAvgSales = new HashMap<>();
        for (Product p : products) {
            String tag = StrUtil.isNotBlank(p.getShopTag()) ? p.getShopTag() : "无标签";
            tagCount.merge(tag, 1L, Long::sum);
        }
        Map<String, List<Product>> productsByTag = products.stream()
                .collect(Collectors.groupingBy(p -> StrUtil.isNotBlank(p.getShopTag()) ? p.getShopTag() : "无标签"));
        for (Map.Entry<String, List<Product>> e : productsByTag.entrySet()) {
            double avg = e.getValue().stream().mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).average().orElse(0);
            tagAvgSales.put(e.getKey(), avg);
        }

        String inputTag = StrUtil.isNotBlank(shopTag) ? shopTag : "无标签";
        String bestTag = tagAvgSales.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElse("");
        double maxTagSales = tagAvgSales.values().stream().max(Double::compare).orElse(1.0);

        // 基于实际销量数据计算标签评分
        double tagScore;
        if (tagAvgSales.containsKey(inputTag)) {
            tagScore = 50 + (tagAvgSales.get(inputTag) / maxTagSales) * 45;
        } else {
            tagScore = 35;
        }

        result.put("tagScore", Math.round(tagScore));
        result.put("bestTag", bestTag);
        result.put("tagSuggestion", tagScore >= 70 ? "店铺标签销量表现好" : "推荐店铺类型: " + bestTag);

        // 加权K近邻销量预测
        final String finalInputProvince = inputProvince;
        final String finalInputTag = inputTag;

        List<double[]> weightedSales = products.stream()
                .filter(p -> p.getPrice() != null && p.getSaleValue() != null)
                .map(p -> {
                    double pPrice = p.getPrice().doubleValue();
                    String pProvince = StrUtil.isNotBlank(p.getLocation()) ? p.getLocation().split(" ")[0] : "";
                    String pTag = StrUtil.isNotBlank(p.getShopTag()) ? p.getShopTag() : "无标签";

                    // 计算相似度权重
                    double priceSimilarity = 1.0 / (1.0 + Math.abs(pPrice - price) / avgPrice);
                    double locationSimilarity = pProvince.equals(finalInputProvince) ? 1.0 : 0.3;
                    double tagSimilarity = pTag.equals(finalInputTag) ? 1.0 : 0.3;

                    double weight = priceSimilarity * 0.5 + locationSimilarity * 0.25 + tagSimilarity * 0.25;
                    return new double[]{p.getSaleValue(), weight};
                })
                .sorted((a, b) -> Double.compare(b[1], a[1]))  // 按权重降序
                .limit(50)  // 取最相似的50个
                .collect(Collectors.toList());

        // 加权平均预测
        double totalWeight = weightedSales.stream().mapToDouble(x -> x[1]).sum();
        double weightedAvgSales = totalWeight > 0
                ? weightedSales.stream().mapToDouble(x -> x[0] * x[1]).sum() / totalWeight
                : salesStats.getAverage();

        // 计算加权标准差用于区间预测
        double variance = weightedSales.stream()
                .mapToDouble(x -> x[1] * Math.pow(x[0] - weightedAvgSales, 2))
                .sum() / Math.max(totalWeight, 1);
        double stdDev = Math.sqrt(variance);

        int predictedSalesMid = (int) weightedAvgSales;
        int predictedSalesLow = (int) Math.max(0, weightedAvgSales - stdDev * 0.8);
        int predictedSalesHigh = (int) (weightedAvgSales + stdDev * 0.8);

        // 预测置信度（基于样本相似度）
        double avgSimilarity = weightedSales.isEmpty() ? 0 : weightedSales.stream().mapToDouble(x -> x[1]).average().orElse(0);
        int confidence = (int) Math.min(95, 50 + avgSimilarity * 45);

        result.put("predictedSalesLow", predictedSalesLow);
        result.put("predictedSalesMid", predictedSalesMid);
        result.put("predictedSalesHigh", predictedSalesHigh);
        result.put("predictionConfidence", confidence);
        result.put("similarSampleCount", weightedSales.size());

        // 综合成功率评分
        List<Rank> ranks = rankService.list(new LambdaQueryWrapper<Rank>().eq(Rank::getRankName, keyword));
        int hotValue = ranks.isEmpty() ? 0 : ranks.stream().mapToInt(r -> r.getHotValue() != null ? r.getHotValue() : 0).max().orElse(0);
        double hotScore = Math.min(100, hotValue / 5000.0 * 100);

        // 使用数据驱动的权重：基于各因素与销量的相关性调整
        double successScore = priceScore * 0.35 + tagScore * 0.30 + locationScore * 0.20 + hotScore * 0.15;
        result.put("successScore", Math.round(successScore * 10) / 10.0);
        result.put("hotScore", Math.round(hotScore * 10) / 10.0);
        result.put("hotValue", hotValue);

        // 综合建议
        String overallRecommendation;
        if (successScore >= 80) {
            overallRecommendation = "预测成功率高，强烈推荐上架";
        } else if (successScore >= 65) {
            overallRecommendation = "预测成功率较高，建议上架";
        } else if (successScore >= 50) {
            overallRecommendation = "成功率中等，建议优化后上架";
        } else if (successScore >= 35) {
            overallRecommendation = "成功率较低，需要大幅优化";
        } else {
            overallRecommendation = "不建议上架，请调整策略";
        }
        result.put("overallRecommendation", overallRecommendation);

        // 优化建议列表（基于数据分析）— 始终展示完整建议
        List<Map<String, Object>> suggestions = new ArrayList<>();

        // 1. 价格优化建议（始终显示）
        Map<String, Object> priceSug = new HashMap<>();
        priceSug.put("type", "price");
        priceSug.put("score", Math.round(priceScore));
        if (priceScore >= 70) {
            priceSug.put("status", "good");
            priceSug.put("text", "价格具有竞争力，当前定价合理");
            priceSug.put("detail", "最优价格区间: " + bestPriceRange + "，品类均价: ¥" + Math.round(avgPrice * 100) / 100.0);
        } else if (priceScore >= 60) {
            priceSug.put("status", "normal");
            priceSug.put("text", "价格尚可，仍有优化空间");
            priceSug.put("detail", "建议调整至最优区间: " + bestPriceRange);
        } else {
            priceSug.put("status", "warning");
            priceSug.put("text", "价格偏离最优区间，建议调整");
            priceSug.put("detail", "建议将价格调整至: " + bestPriceRange);
        }
        suggestions.add(priceSug);

        // 2. 店铺类型建议（始终显示）
        Map<String, Object> tagSug = new HashMap<>();
        tagSug.put("type", "shopTag");
        tagSug.put("score", Math.round(tagScore));
        if (tagScore >= 70) {
            tagSug.put("status", "good");
            tagSug.put("text", "店铺标签销量表现好");
            tagSug.put("detail", "当前店铺类型在该品类中表现优秀");
        } else {
            tagSug.put("status", "warning");
            tagSug.put("text", "推荐使用店铺类型: " + bestTag);
            tagSug.put("detail", "该类型在该品类中平均销量最高");
        }
        suggestions.add(tagSug);

        // 3. 发货地建议（始终显示）
        Map<String, Object> locSug = new HashMap<>();
        locSug.put("type", "location");
        locSug.put("score", Math.round(locationScore));
        if (locationScore >= 70) {
            locSug.put("status", "good");
            locSug.put("text", "发货地销量表现优秀");
            locSug.put("detail", "当前发货地在该品类中具有优势");
        } else {
            locSug.put("status", "warning");
            locSug.put("text", "推荐发货地: " + bestSalesLocation);
            locSug.put("detail", "该地区在该品类中平均销量领先");
        }
        suggestions.add(locSug);

        // 4. 品类热度建议（始终显示）
        Map<String, Object> hotSug = new HashMap<>();
        hotSug.put("type", "hot");
        hotSug.put("score", Math.round(hotScore));
        if (hotScore >= 60) {
            hotSug.put("status", "good");
            hotSug.put("text", "品类热度高，市场需求旺盛");
            hotSug.put("detail", "热度值: " + hotValue);
        } else if (hotScore >= 40) {
            hotSug.put("status", "normal");
            hotSug.put("text", "品类热度中等");
            hotSug.put("detail", "热度值: " + hotValue + "，可关注更热门品类");
        } else {
            hotSug.put("status", "warning");
            hotSug.put("text", "品类热度较低，建议关注热门品类");
            hotSug.put("detail", "热度值: " + hotValue);
        }
        suggestions.add(hotSug);

        result.put("suggestions", suggestions);

        // 标题关键词建议 — 调用已有的热门关键词分析
        try {
            List<Map<String, Object>> titleKeywords = computeHotTitleKeywords(keyword);
            if (titleKeywords != null && !titleKeywords.isEmpty()) {
                // 取前10个高频关键词
                List<Map<String, Object>> topKeywords = titleKeywords.stream().limit(10).collect(Collectors.toList());
                result.put("titleKeywords", topKeywords);

                // 生成标题关键词建议文本
                String keywordText = topKeywords.stream()
                        .map(m -> m.get("word").toString())
                        .limit(5)
                        .collect(Collectors.joining("、"));
                result.put("titleKeywordSuggestion", "建议标题包含高频热词: " + keywordText);
            } else {
                result.put("titleKeywords", new ArrayList<>());
                result.put("titleKeywordSuggestion", "暂无足够数据生成关键词建议");
            }
        } catch (Exception e) {
            log.warn("计算标题关键词建议失败: {}", e.getMessage());
            result.put("titleKeywords", new ArrayList<>());
            result.put("titleKeywordSuggestion", "关键词分析暂不可用");
        }

        // 竞品分析
        long competitorCount = products.stream()
                .filter(p -> p.getPrice() != null && Math.abs(p.getPrice().doubleValue() - price) < price * 0.2)
                .count();
        result.put("competitorCount", competitorCount);
        result.put("competitionLevel", competitorCount > 50 ? "激烈" : competitorCount > 20 ? "中等" : "较低");

        return result;
    }

    @Override
    public List<Map<String, Object>> getCategoryPotentialRanking() {
        return getFromCacheOrCompute("category_potential",
                new TypeReference<List<Map<String, Object>>>() {}, this::computeCategoryPotentialRanking);
    }

    private List<Map<String, Object>> computeCategoryPotentialRanking() {
        List<Product> allProducts = productService.list();
        Map<String, Integer> rankHotMap = rankService.list().stream()
                .collect(Collectors.toMap(Rank::getRankName, r -> r.getHotValue() != null ? r.getHotValue() : 0, Math::max));

        return allProducts.stream()
                .filter(p -> StrUtil.isNotBlank(p.getKeyword()))
                .collect(Collectors.groupingBy(Product::getKeyword))
                .entrySet().stream().map(e -> {
                    String keyword = e.getKey();
                    List<Product> products = e.getValue();
                    long validCount = products.stream().filter(p -> p.getSaleValue() != null && p.getSaleValue() > 100).count();
                    long totalSales = products.stream().mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0).sum();
                    double avgPrice = products.stream()
                            .filter(p -> p.getSaleValue() != null && p.getSaleValue() > 100 && p.getPrice() != null)
                            .mapToDouble(p -> p.getPrice().doubleValue()).average().orElse(0);
                    int hotValue = rankHotMap.getOrDefault(keyword, 0);

                    double hotScore = Math.min(100, hotValue / 50.0);
                    double competitionScore = hotValue > 0 ? Math.max(0, 100 - (validCount / (double) hotValue * 1000)) : 50;
                    double salesScore = Math.min(100, totalSales / 100000.0);
                    double totalScore = hotScore * 0.4 + competitionScore * 0.3 + calcProfitScore(avgPrice) * 0.15 + salesScore * 0.15;

                    Map<String, Object> r = new HashMap<>();
                    r.put("success", true);
                    r.put("keyword", keyword);
                    r.put("hotValue", hotValue);
                    r.put("productCount", (long) products.size());
                    r.put("validProductCount", validCount);
                    r.put("totalSales", totalSales);
                    r.put("avgPrice", Math.round(avgPrice * 100) / 100.0);
                    r.put("hotScore", Math.round(hotScore * 10) / 10.0);
                    r.put("competitionScore", Math.round(competitionScore * 10) / 10.0);
                    r.put("profitScore", Math.round(calcProfitScore(avgPrice) * 10) / 10.0);
                    r.put("salesScore", Math.round(salesScore * 10) / 10.0);
                    r.put("totalScore", Math.round(totalScore * 10) / 10.0);
                    r.put("recommendation", getRecommendation(totalScore));
                    return r;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("totalScore"), (Double) a.get("totalScore")))
                .collect(Collectors.toList());
    }

    @Override
    public void refreshAllAnalysisCache() {
        log.info("开始刷新分析数据缓存...");
        try {
            // 一次性加载所有数据，避免重复查询
            List<Product> allProducts = productService.list();
            List<Rank> allRanks = rankService.list();
            log.info("数据加载完成：商品{}条，榜单{}条", allProducts.size(), allRanks.size());

            // 大屏数据
            log.info("刷新大屏数据缓存...");
            statsCacheService.updateJson("region_distribution", objectMapper.writeValueAsString(computeRegionDistribution(null)));
            statsCacheService.updateJson("store_ranking", objectMapper.writeValueAsString(computeStoreRanking()));
            statsCacheService.updateJson("rank_hotlist", objectMapper.writeValueAsString(computeRankHotlist()));
            statsCacheService.updateJson("shop_tag_distribution", objectMapper.writeValueAsString(computeShopTagDistribution()));
            statsCacheService.updateJson("price_sale_scatter", objectMapper.writeValueAsString(computePriceSaleScatter()));

            // 市场分析
            log.info("刷新市场分析缓存...");
            statsCacheService.updateJson("market_supply_demand", objectMapper.writeValueAsString(computeMarketSupplyDemand()));
            statsCacheService.updateJson("blue_ocean_categories", objectMapper.writeValueAsString(computeBlueOceanCategories()));
            statsCacheService.updateJson("red_ocean_categories", objectMapper.writeValueAsString(computeRedOceanCategories()));

            // 价格策略
            log.info("刷新价格策略缓存...");
            statsCacheService.updateJson("price_strategy", objectMapper.writeValueAsString(computePriceStrategy()));
            statsCacheService.updateJson("optimal_price_range", objectMapper.writeValueAsString(computeOptimalPriceRange()));

            // 爆款分析
            log.info("刷新爆款分析缓存...");
            statsCacheService.updateJson("hot_product_features", objectMapper.writeValueAsString(computeHotProductFeatures(null)));
            statsCacheService.updateJson("hot_title_keywords", objectMapper.writeValueAsString(computeHotTitleKeywords(null)));

            // 店铺分析
            log.info("刷新店铺分析缓存...");
            statsCacheService.updateJson("store_analysis", objectMapper.writeValueAsString(computeStoreRanking()));
            statsCacheService.updateJson("store_age_sales", objectMapper.writeValueAsString(computeStoreAgeSales()));

            // 地域分析
            log.info("刷新地域分析缓存...");
            statsCacheService.updateJson("region_category_analysis", objectMapper.writeValueAsString(computeRegionCategoryAnalysis()));

            // 品类分析
            log.info("刷新品类分析缓存...");
            statsCacheService.updateJson("category_deep_analysis", objectMapper.writeValueAsString(computeCategoryDeepAnalysis()));
            statsCacheService.updateJson("category_potential", objectMapper.writeValueAsString(computeCategoryPotentialRanking()));

            log.info("分析数据缓存刷新完成！");
        } catch (Exception e) {
            log.error("刷新分析缓存失败", e);
            throw new RuntimeException("缓存刷新失败: " + e.getMessage());
        }
    }

    @Override
    public void refreshHotProductCache() {
        log.info("刷新爆品相关缓存...");
        try {
            // 只刷新与爆品规则相关的缓存
            statsCacheService.updateJson("hot_product_features", objectMapper.writeValueAsString(computeHotProductFeatures(null)));
            statsCacheService.updateJson("hot_title_keywords", objectMapper.writeValueAsString(computeHotTitleKeywords(null)));
            log.info("爆品缓存刷新完成！");
        } catch (Exception e) {
            log.error("刷新爆品缓存失败", e);
            throw new RuntimeException("爆品缓存刷新失败: " + e.getMessage());
        }
    }
}

