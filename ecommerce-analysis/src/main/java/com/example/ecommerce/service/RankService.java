package com.example.ecommerce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ecommerce.entity.Rank;
import java.util.List;
import java.util.Map;

public interface RankService extends IService<Rank> {
    Page<Rank> pageList(Integer pageNum, Integer pageSize, String category);
    void importFromExcel(String filePath);
    List<Map<String, Object>> getCategoryStats();
    List<Rank> getTopRanks(Integer limit);
}
