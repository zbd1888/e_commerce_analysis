package com.example.ecommerce.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    // 内存缓存：关键词列表、发货地列表（避免频繁查询数据库）
    private volatile List<String> cachedKeywords = null;
    private volatile List<String> cachedLocations = null;
    private volatile List<String> cachedProvinces = null;
    private volatile long cacheExpireTime = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5分钟缓存

    @Override
    public Page<Product> pageList(Integer pageNum, Integer pageSize, String keyword, String store, String province,
                                   Double minPrice, Double maxPrice, Long minSales, Long maxSales) {
        Page<Product> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(Product::getKeyword, keyword).or().like(Product::getTitle, keyword));
        }
        if (StrUtil.isNotBlank(store)) {
            wrapper.like(Product::getStore, store);
        }
        if (StrUtil.isNotBlank(province)) {
            wrapper.like(Product::getLocation, province);
        }
        // 价格区间筛选
        if (minPrice != null) {
            wrapper.ge(Product::getPrice, BigDecimal.valueOf(minPrice));
        }
        if (maxPrice != null) {
            wrapper.le(Product::getPrice, BigDecimal.valueOf(maxPrice));
        }
        // 销量区间筛选
        if (minSales != null) {
            wrapper.ge(Product::getSaleValue, minSales);
        }
        if (maxSales != null) {
            wrapper.le(Product::getSaleValue, maxSales);
        }
        wrapper.orderByDesc(Product::getSaleValue);

        return page(page, wrapper);
    }

    @Override
    public void importFromExcel(String filePath) {
        List<Map<Integer, String>> dataList = EasyExcel.read(filePath).sheet().doReadSync();
        List<Product> products = new ArrayList<>();
        
        for (int i = 1; i < dataList.size(); i++) {
            Map<Integer, String> row = dataList.get(i);
            Product p = new Product();
            p.setProductId(row.get(0));
            p.setPicUrl(row.get(1));
            p.setTitle(row.get(2));
            p.setPrice(parseBigDecimal(row.get(3)));
            p.setSaleNum(row.get(4));
            p.setSaleValue(parseSaleValue(row.get(4)));
            p.setStore(row.get(5));
            p.setShopUrl(row.get(6));
            p.setKeyword(row.get(7));
            String location = row.get(8);
            p.setLocation(location);
            if (StrUtil.isNotBlank(location) && location.contains(" ")) {
                String[] parts = location.split(" ");
                p.setProvince(parts[0]);
                p.setCity(parts.length > 1 ? parts[1] : "");
            }
            p.setShopTag(row.get(9));
            p.setCouponPrice(parseBigDecimal(row.get(10)));
            p.setIsCleaned(0);
            products.add(p);
        }
        saveBatch(products, 500);
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return StrUtil.isNotBlank(value) ? new BigDecimal(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseSaleValue(String saleNum) {
        if (StrUtil.isBlank(saleNum)) return 0;
        Pattern pattern = Pattern.compile("([\\d.]+)\\s*(万)?");
        Matcher matcher = pattern.matcher(saleNum);
        if (matcher.find()) {
            double num = Double.parseDouble(matcher.group(1));
            if ("万".equals(matcher.group(2))) {
                num *= 10000;
            }
            return (int) num;
        }
        return 0;
    }

    /**
     * 检查缓存是否过期
     */
    private boolean isCacheExpired() {
        return System.currentTimeMillis() > cacheExpireTime;
    }

    /**
     * 刷新缓存
     */
    private synchronized void refreshCacheIfNeeded() {
        if (isCacheExpired()) {
            // 使用数据库原生 DISTINCT 查询，性能远优于加载全表
            cachedKeywords = baseMapper.selectDistinctKeywords();
            cachedLocations = baseMapper.selectDistinctProvinces(); // 使用省份，更精简
            cacheExpireTime = System.currentTimeMillis() + CACHE_DURATION_MS;
        }
    }

    /**
     * 获取所有关键词列表（用于下拉筛选）
     * 使用数据库原生 DISTINCT 查询 + 内存缓存，性能极大提升
     */
    @Override
    public List<String> getKeywordList() {
        refreshCacheIfNeeded();
        return cachedKeywords != null ? new ArrayList<>(cachedKeywords) : new ArrayList<>();
    }

    /**
     * 获取所有店铺列表（用于下拉筛选）
     * 使用数据库原生 DISTINCT 查询，性能远优于加载全表
     */
    @Override
    public List<String> getStoreList() {
        return baseMapper.selectDistinctStores();
    }

    /**
     * 根据关键词筛选商品列表
     */
    private List<Product> getProductsByKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return list();
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getKeyword, keyword);
        return list(wrapper);
    }

    // 辅助方法：创建名称-值映射
    private Map<String, Object> nameValueMap(String name, Long value) {
        Map<String, Object> m = new HashMap<>(); m.put("name", name); m.put("value", value); return m;
    }

    @Override
    public List<Map<String, Object>> getKeywordStats() {
        return list().stream().filter(p -> StrUtil.isNotBlank(p.getKeyword()))
                .collect(Collectors.groupingBy(Product::getKeyword, Collectors.counting()))
                .entrySet().stream().map(e -> nameValueMap(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare((Long)b.get("value"), (Long)a.get("value")))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getProvinceStats(String keyword) {
        List<Product> products = StrUtil.isBlank(keyword) ? list() : getProductsByKeyword(keyword);
        return products.stream().filter(p -> StrUtil.isNotBlank(p.getProvince()))
                .collect(Collectors.groupingBy(Product::getProvince, Collectors.counting()))
                .entrySet().stream().map(e -> nameValueMap(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare((Long)b.get("value"), (Long)a.get("value")))
                .limit(10).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getStoreStats(Integer limit, String keyword) {
        List<Product> products = StrUtil.isBlank(keyword) ? list() : getProductsByKeyword(keyword);
        return products.stream().filter(p -> StrUtil.isNotBlank(p.getStore()))
                .collect(Collectors.groupingBy(Product::getStore, Collectors.counting()))
                .entrySet().stream().map(e -> nameValueMap(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare((Long)b.get("value"), (Long)a.get("value")))
                .limit(limit != null ? limit : 10).collect(Collectors.toList());
    }

    /**
     * 店铺深度分析（按销量汇总，这是正确的统计方式！）
     * 返回：店铺名称、总销量、商品数量、平均价格
     */
    @Override
    public List<Map<String, Object>> getStoreDetailStats(Integer limit, String keyword) {
        List<Product> products = StrUtil.isBlank(keyword) ? list() : getProductsByKeyword(keyword);

        // 按店铺分组
        Map<String, List<Product>> storeGroups = products.stream()
                .filter(p -> StrUtil.isNotBlank(p.getStore()))
                .collect(Collectors.groupingBy(Product::getStore));

        return storeGroups.entrySet().stream()
                .map(e -> {
                    List<Product> storeProducts = e.getValue();
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", e.getKey());
                    // 销量总和（核心指标）
                    long totalSales = storeProducts.stream()
                            .mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0)
                            .sum();
                    map.put("value", totalSales);
                    map.put("totalSales", totalSales);
                    // 商品数量
                    map.put("productCount", storeProducts.size());
                    // 平均价格
                    double avgPrice = storeProducts.stream()
                            .filter(p -> p.getPrice() != null)
                            .mapToDouble(p -> p.getPrice().doubleValue())
                            .average().orElse(0);
                    map.put("avgPrice", String.format("%.2f", avgPrice));
                    // 爆款数（销量>=10000的商品数）
                    long hotCount = storeProducts.stream()
                            .filter(p -> p.getSaleValue() != null && p.getSaleValue() >= 10000)
                            .count();
                    map.put("hotCount", hotCount);
                    return map;
                })
                .sorted((a, b) -> Long.compare((Long)b.get("totalSales"), (Long)a.get("totalSales")))
                .limit(limit != null ? limit : 10)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getPriceRangeStats(String keyword) {
        List<Product> products = StrUtil.isBlank(keyword) ? list() : getProductsByKeyword(keyword);
        Map<String, Long> ranges = new LinkedHashMap<>();
        ranges.put("0-50", 0L);
        ranges.put("50-200", 0L);
        ranges.put("200-1000", 0L);
        ranges.put("1000+", 0L);

        for (Product p : products) {
            if (p.getPrice() == null) continue;
            double price = p.getPrice().doubleValue();
            if (price < 50) ranges.merge("0-50", 1L, Long::sum);
            else if (price < 200) ranges.merge("50-200", 1L, Long::sum);
            else if (price < 1000) ranges.merge("200-1000", 1L, Long::sum);
            else ranges.merge("1000+", 1L, Long::sum);
        }

        return ranges.entrySet().stream()
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", e.getKey());
                    map.put("value", e.getValue());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getOverviewStats(String keyword) {
        List<Product> products = StrUtil.isBlank(keyword) ? list() : getProductsByKeyword(keyword);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProducts", products.size());

        double avgPrice = products.stream()
                .filter(p -> p.getPrice() != null)
                .mapToDouble(p -> p.getPrice().doubleValue())
                .average().orElse(0);
        stats.put("avgPrice", String.format("%.2f", avgPrice));

        // 总销量
        long totalSales = products.stream()
                .mapToLong(p -> p.getSaleValue() != null ? p.getSaleValue() : 0)
                .sum();
        stats.put("totalSales", totalSales);

        // 店铺数量
        long storeCount = products.stream()
                .map(Product::getStore)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .count();
        stats.put("storeCount", storeCount);

        // 如果没有关键词筛选，返回关键词数量
        if (StrUtil.isBlank(keyword)) {
            stats.put("keywordCount", products.stream()
                    .map(Product::getKeyword)
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .count());
        }

        return stats;
    }

    @Override
    public Map<String, Object> getDataQuality() {
        List<Product> products = list();
        long total = products.size();
        if (total == 0) {
            Map<String, Object> quality = new HashMap<>();
            quality.put("completeness", 100);
            quality.put("priceValid", 100);
            quality.put("saleValid", 100);
            quality.put("imageValid", 100);
            return quality;
        }

        // 数据完整性：有标题的商品比例
        long hasTitle = products.stream().filter(p -> StrUtil.isNotBlank(p.getTitle())).count();
        // 价格有效率：价格大于0的商品比例
        long validPrice = products.stream().filter(p -> p.getPrice() != null && p.getPrice().doubleValue() > 0).count();
        // 销量有效率：销量大于0的商品比例
        long validSales = products.stream().filter(p -> p.getSaleValue() != null && p.getSaleValue() > 0).count();
        // 图片有效率：有图片URL的商品比例
        long hasImage = products.stream().filter(p -> StrUtil.isNotBlank(p.getPicUrl())).count();

        Map<String, Object> quality = new HashMap<>();
        quality.put("completeness", Math.round(hasTitle * 100.0 / total));
        quality.put("priceValid", Math.round(validPrice * 100.0 / total));
        quality.put("saleValid", Math.round(validSales * 100.0 / total));
        quality.put("imageValid", Math.round(hasImage * 100.0 / total));
        return quality;
    }

    /**
     * 获取发货地列表（用于下拉筛选）
     * 使用数据库原生 DISTINCT 查询 + 内存缓存
     */
    @Override
    public List<String> getLocationList() {
        refreshCacheIfNeeded();
        return cachedLocations != null ? new ArrayList<>(cachedLocations) : new ArrayList<>();
    }

    /**
     * 清除缓存（当数据发生变化时调用）
     */
    public void clearCache() {
        cacheExpireTime = 0;
        cachedKeywords = null;
        cachedLocations = null;
        cachedProvinces = null;
    }
}
