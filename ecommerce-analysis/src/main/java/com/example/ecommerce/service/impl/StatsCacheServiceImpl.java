package com.example.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.StatsCache;
import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.mapper.StatsCacheMapper;
import com.example.ecommerce.service.ProductService;
import com.example.ecommerce.service.StatsCacheService;
import com.example.ecommerce.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsCacheServiceImpl extends ServiceImpl<StatsCacheMapper, StatsCache> implements StatsCacheService {

    @Lazy
    private final ProductService productService;
    @Lazy
    private final UserService userService;

    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Long getValue(String key) {
        StatsCache cache = getOne(new LambdaQueryWrapper<StatsCache>().eq(StatsCache::getStatKey, key));
        return cache != null ? cache.getStatValue() : 0L;
    }

    @Override
    public BigDecimal getPercent(String key) {
        StatsCache cache = getOne(new LambdaQueryWrapper<StatsCache>().eq(StatsCache::getStatKey, key));
        return cache != null ? cache.getStatPercent() : BigDecimal.ZERO;
    }

    @Override
    public String getJson(String key) {
        StatsCache cache = getOne(new LambdaQueryWrapper<StatsCache>().eq(StatsCache::getStatKey, key));
        return cache != null ? cache.getStatJson() : null;
    }

    @Override
    public void updateValue(String key, Long value) {
        update(new LambdaUpdateWrapper<StatsCache>()
                .eq(StatsCache::getStatKey, key)
                .set(StatsCache::getStatValue, value));
    }

    @Override
    public void updatePercent(String key, BigDecimal percent) {
        update(new LambdaUpdateWrapper<StatsCache>()
                .eq(StatsCache::getStatKey, key)
                .set(StatsCache::getStatPercent, percent));
    }

    @Override
    public void updateJson(String key, String json) {
        update(new LambdaUpdateWrapper<StatsCache>()
                .eq(StatsCache::getStatKey, key)
                .set(StatsCache::getStatJson, json));
    }

    @Override
    public void refreshAllStats() {
        // 1. 更新商品总数
        long totalProducts = productService.count();
        updateValue("total_products", totalProducts);

        // 2. 更新品类总数
        List<String> keywords = productService.getKeywordList();
        updateValue("total_categories", (long) keywords.size());

        // 3. 更新用户总数
        long totalUsers = userService.count();
        updateValue("total_users", totalUsers);

        // 4. 更新数据质量统计（使用数据库聚合查询而非加载全部数据）
        refreshQualityStats();

        // 5. 更新用户大屏统计数据
        refreshUserDashboardStats();

        // 6. 更新今日新增和有效商品数（系统监控页面需要）
        refreshMonitorStats();
    }

    private void refreshMonitorStats() {
        // 今日新增商品数（根据created_at字段判断）
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime startOfDay = today.atStartOfDay();
        long todayNew = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .ge(Product::getCreatedAt, startOfDay));
        updateValue("today_new_products", todayNew);

        // 有效商品数（有标题、有价格、有销量的商品）
        long validProducts = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getTitle).ne(Product::getTitle, "")
                .isNotNull(Product::getPrice).gt(Product::getPrice, 0)
                .isNotNull(Product::getSaleValue).ge(Product::getSaleValue, 0));
        updateValue("valid_products", validProducts);
    }

    private void refreshQualityStats() {
        long total = productService.count();
        if (total == 0) {
            updatePercent("quality_completeness", BigDecimal.valueOf(100));
            updatePercent("quality_price_valid", BigDecimal.valueOf(100));
            updatePercent("quality_sale_valid", BigDecimal.valueOf(100));
            updatePercent("quality_image_valid", BigDecimal.valueOf(100));
            return;
        }

        // 使用数据库COUNT查询而非加载全部数据到内存
        long hasTitle = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getTitle).ne(Product::getTitle, ""));
        long validPrice = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getPrice).gt(Product::getPrice, 0));
        long validSales = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getSaleValue).gt(Product::getSaleValue, 0));
        long hasImage = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getPicUrl).ne(Product::getPicUrl, ""));

        updatePercent("quality_completeness", BigDecimal.valueOf(Math.round(hasTitle * 100.0 / total)));
        updatePercent("quality_price_valid", BigDecimal.valueOf(Math.round(validPrice * 100.0 / total)));
        updatePercent("quality_sale_valid", BigDecimal.valueOf(Math.round(validSales * 100.0 / total)));
        updatePercent("quality_image_valid", BigDecimal.valueOf(Math.round(hasImage * 100.0 / total)));
    }

    @Override
    public Map<String, Object> getAdminStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProducts", getValue("total_products"));
        stats.put("totalCategories", getValue("total_categories"));
        stats.put("totalUsers", getValue("total_users"));
        stats.put("todayNewProducts", getValue("today_new_products"));
        stats.put("validProducts", getValue("valid_products"));
        return stats;
    }

    @Override
    public Map<String, Object> getDataQuality() {
        Map<String, Object> quality = new HashMap<>();
        quality.put("completeness", getPercent("quality_completeness"));
        quality.put("priceValid", getPercent("quality_price_valid"));
        quality.put("saleValid", getPercent("quality_sale_valid"));
        quality.put("imageValid", getPercent("quality_image_valid"));
        return quality;
    }

    private void refreshUserDashboardStats() {
        try {
            // 使用数据库聚合查询，避免加载全部数据到内存

            // 总销量（数据库聚合）
            Long totalSales = productMapper.selectTotalSales();
            updateValue("total_sales", totalSales != null ? totalSales : 0L);

            // 平均价格（数据库聚合）
            BigDecimal avgPrice = productMapper.selectAvgPrice();
            updatePercent("avg_price", avgPrice != null ? avgPrice.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

            // 品类销量统计TOP15（数据库聚合）
            List<Map<String, Object>> categorySales = productMapper.selectCategorySalesTop15();
            updateJson("category_sales", objectMapper.writeValueAsString(categorySales));

            // 价格区间分布（数据库聚合）
            Map<String, Object> priceRanges = productMapper.selectPriceDistribution();
            String[] labels = {"0-100元", "100-300元", "300-500元", "500-1000元", "1000元以上"};
            List<Map<String, Object>> priceDistribution = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> m = new HashMap<>();
                m.put("range", labels[i]);
                Object val = priceRanges.get("range" + i);
                m.put("count", val != null ? ((Number) val).intValue() : 0);
                priceDistribution.add(m);
            }
            updateJson("price_distribution", objectMapper.writeValueAsString(priceDistribution));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Object> getUserDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProducts", getValue("total_products"));
        stats.put("totalCategories", getValue("total_categories"));
        stats.put("totalSales", getValue("total_sales"));
        stats.put("avgPrice", getPercent("avg_price"));
        return stats;
    }

    @Override
    public String getCategorySalesJson() {
        return getJson("category_sales");
    }

    @Override
    public String getPriceDistributionJson() {
        return getJson("price_distribution");
    }
}

