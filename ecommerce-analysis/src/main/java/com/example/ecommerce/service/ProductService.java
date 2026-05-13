package com.example.ecommerce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ecommerce.entity.Product;
import java.util.List;
import java.util.Map;

public interface ProductService extends IService<Product> {
    // 分页查询（支持多条件筛选）
    Page<Product> pageList(Integer pageNum, Integer pageSize, String keyword, String store, String province,
                           Double minPrice, Double maxPrice, Long minSales, Long maxSales);

    void importFromExcel(String filePath);

    // 获取所有关键词列表（用于下拉筛选）
    List<String> getKeywordList();

    // 获取店铺列表（用于下拉筛选）
    List<String> getStoreList();

    // 统计接口（支持按关键词筛选）
    List<Map<String, Object>> getKeywordStats();
    List<Map<String, Object>> getProvinceStats(String keyword);
    List<Map<String, Object>> getStoreStats(Integer limit, String keyword);
    List<Map<String, Object>> getPriceRangeStats(String keyword);
    Map<String, Object> getOverviewStats(String keyword);

    // 店铺深度分析（按销量汇总）
    List<Map<String, Object>> getStoreDetailStats(Integer limit, String keyword);

    // 数据质量统计
    Map<String, Object> getDataQuality();

    // 获取发货地列表
    List<String> getLocationList();
}
