package com.example.ecommerce.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ecommerce.entity.Rank;
import com.example.ecommerce.mapper.RankMapper;
import com.example.ecommerce.service.RankService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import cn.hutool.core.util.StrUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankServiceImpl extends ServiceImpl<RankMapper, Rank> implements RankService {

    @Override
    public Page<Rank> pageList(Integer pageNum, Integer pageSize, String category) {
        Page<Rank> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Rank> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(Rank::getCategory, category);
        }
        wrapper.orderByDesc(Rank::getHotValue);
        return page(page, wrapper);
    }

    @Override
    public void importFromExcel(String filePath) {
        List<Map<Integer, String>> dataList = EasyExcel.read(filePath).sheet().doReadSync();
        List<Rank> ranks = new ArrayList<>();

        for (int i = 1; i < dataList.size(); i++) {
            Map<Integer, String> row = dataList.get(i);
            Rank r = new Rank();
            r.setCategory(row.get(0));
            r.setRankName(row.get(1));
            r.setHotDesc(row.get(2));
            r.setHotValue(parseHotValue(row.get(2)));
            ranks.add(r);
        }
        saveBatch(ranks, 500);
    }

    private Integer parseHotValue(String hotDesc) {
        if (StrUtil.isBlank(hotDesc)) return 0;
        Pattern pattern = Pattern.compile("([\\d.]+)\\s*(\u4e07)?");
        Matcher matcher = pattern.matcher(hotDesc);
        if (matcher.find()) {
            double num = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            if ("\u4e07".equals(unit)) {
                num *= 10000;
            }
            return (int) num;
        }
        return 0;
    }

    @Override
    public List<Map<String, Object>> getCategoryStats() {
        return list().stream().filter(r -> StrUtil.isNotBlank(r.getCategory()))
                .collect(Collectors.groupingBy(Rank::getCategory, Collectors.counting()))
                .entrySet().stream()
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("name", e.getKey()); m.put("value", e.getValue()); return m; })
                .sorted((a, b) -> Long.compare((Long)b.get("value"), (Long)a.get("value")))
                .collect(Collectors.toList());
    }

    @Override
    public List<Rank> getTopRanks(Integer limit) {
        return list(new LambdaQueryWrapper<Rank>()
                .orderByDesc(Rank::getHotValue)
                .last("LIMIT " + (limit != null ? limit : 10)));
    }
}