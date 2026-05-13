package com.example.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ecommerce.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 使用数据库原生查询获取去重关键词列表，性能远优于加载全表
     */
    @Select("SELECT DISTINCT keyword FROM tb_product WHERE keyword IS NOT NULL AND keyword != '' ORDER BY keyword")
    List<String> selectDistinctKeywords();

    /**
     * 使用数据库原生查询获取去重发货地列表
     */
    @Select("SELECT DISTINCT location FROM tb_product WHERE location IS NOT NULL AND location != '' ORDER BY location")
    List<String> selectDistinctLocations();

    /**
     * 使用数据库原生查询获取去重省份列表
     */
    @Select("SELECT DISTINCT province FROM tb_product WHERE province IS NOT NULL AND province != '' ORDER BY province")
    List<String> selectDistinctProvinces();

    /**
     * 使用数据库原生查询获取去重店铺列表（限制数量避免数据过多）
     */
    @Select("SELECT DISTINCT store FROM tb_product WHERE store IS NOT NULL AND store != '' ORDER BY store LIMIT 200")
    List<String> selectDistinctStores();

    /**
     * 聚合查询：总销量
     */
    @Select("SELECT COALESCE(SUM(sale_value), 0) FROM tb_product")
    Long selectTotalSales();

    /**
     * 聚合查询：平均价格
     */
    @Select("SELECT COALESCE(AVG(price), 0) FROM tb_product WHERE price IS NOT NULL AND price > 0")
    BigDecimal selectAvgPrice();

    /**
     * 聚合查询：按品类统计销量TOP15
     */
    @Select("SELECT keyword, SUM(COALESCE(sale_value, 0)) as totalSales FROM tb_product WHERE keyword IS NOT NULL AND keyword != '' GROUP BY keyword ORDER BY totalSales DESC LIMIT 15")
    List<Map<String, Object>> selectCategorySalesTop15();

    /**
     * 聚合查询：价格区间分布
     */
    @Select("SELECT " +
            "SUM(CASE WHEN price < 100 THEN 1 ELSE 0 END) as range0, " +
            "SUM(CASE WHEN price >= 100 AND price < 300 THEN 1 ELSE 0 END) as range1, " +
            "SUM(CASE WHEN price >= 300 AND price < 500 THEN 1 ELSE 0 END) as range2, " +
            "SUM(CASE WHEN price >= 500 AND price < 1000 THEN 1 ELSE 0 END) as range3, " +
            "SUM(CASE WHEN price >= 1000 THEN 1 ELSE 0 END) as range4 " +
            "FROM tb_product WHERE price IS NOT NULL")
    Map<String, Object> selectPriceDistribution();
}
