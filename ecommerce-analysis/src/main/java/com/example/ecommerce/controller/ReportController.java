package com.example.ecommerce.controller;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.service.AnalysisService;
import com.example.ecommerce.service.ProductService;
import com.example.ecommerce.service.StatsCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Tag(name = "报告导出")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ProductService productService;
    private final AnalysisService analysisService;
    private final StatsCacheService statsCacheService;

    @Operation(summary = "下载商品数据Excel")
    @GetMapping("/export/products")
    public ResponseEntity<byte[]> exportProducts(@RequestParam(required = false) String keyword) {
        try {
            List<Product> products = keyword != null && !keyword.isEmpty()
                    ? productService.pageList(1, 10000, keyword, null, null, null, null, null, null).getRecords()
                    : productService.list();

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("商品数据");

            // 表头
            Row header = sheet.createRow(0);
            String[] headers = {"商品ID", "标题", "价格", "销量", "店铺", "发货地", "品类", "店铺标签"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // 数据行
            int rowNum = 1;
            for (Product p : products) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(p.getId() != null ? p.getId() : 0);
                row.createCell(1).setCellValue(p.getTitle() != null ? p.getTitle() : "");
                row.createCell(2).setCellValue(p.getPrice() != null ? p.getPrice().doubleValue() : 0);
                row.createCell(3).setCellValue(p.getSaleValue() != null ? p.getSaleValue() : 0);
                row.createCell(4).setCellValue(p.getStore() != null ? p.getStore() : "");
                row.createCell(5).setCellValue(p.getLocation() != null ? p.getLocation() : "");
                row.createCell(6).setCellValue(p.getKeyword() != null ? p.getKeyword() : "");
                row.createCell(7).setCellValue(p.getShopTag() != null ? p.getShopTag() : "");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();

            String fileName = "商品数据_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(out.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "下载市场分析报告Excel")
    @GetMapping("/export/market-analysis")
    public ResponseEntity<byte[]> exportMarketAnalysis() {
        try {
            Workbook workbook = new XSSFWorkbook();

            // Sheet1: 市场供需分析
            createAnalysisSheet(workbook, "市场供需分析", analysisService.getMarketSupplyDemand(),
                    new String[]{"品类", "天猫分类", "榜单热度", "商品数量", "总销量", "平均价格", "供需比", "竞争指数"},
                    new String[]{"keyword", "category", "hotValue", "productCount", "totalSales", "avgPrice", "supplyDemandRatio", "competitionIndex"});

            // Sheet2: 蓝海品类
            createAnalysisSheet(workbook, "蓝海品类", analysisService.getBlueOceanCategories(),
                    new String[]{"品类", "榜单热度", "商品数量", "供需比"},
                    new String[]{"keyword", "hotValue", "productCount", "supplyDemandRatio"});

            // Sheet3: 红海品类
            createAnalysisSheet(workbook, "红海品类", analysisService.getRedOceanCategories(),
                    new String[]{"品类", "榜单热度", "商品数量", "供需比"},
                    new String[]{"keyword", "hotValue", "productCount", "supplyDemandRatio"});

            // Sheet4: 爆款特征 - getHotProductFeatures返回的是Map，需要提取categoryFeatures列表
            Map<String, Object> hotFeatures = analysisService.getHotProductFeatures("");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> categoryFeatures = hotFeatures.get("categoryFeatures") != null
                    ? (List<Map<String, Object>>) hotFeatures.get("categoryFeatures")
                    : new java.util.ArrayList<>();
            createAnalysisSheet(workbook, "爆款特征", categoryFeatures,
                    new String[]{"品类", "样本数", "价格区间", "均价", "平均销量", "主流标签", "发货地"},
                    new String[]{"keyword", "sampleSize", "priceRange", "avgPrice", "avgSales", "topTag", "topLocation"});

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();

            String fileName = "市场分析报告_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(out.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private void createAnalysisSheet(Workbook workbook, String sheetName, List<Map<String, Object>> data, String[] headers, String[] keys) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        int rowNum = 1;
        for (Map<String, Object> item : data) {
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < keys.length; i++) {
                Object val = item.get(keys[i]);
                row.createCell(i).setCellValue(val != null ? val.toString() : "");
            }
        }
    }

    @Operation(summary = "刷新分析缓存")
    @PostMapping("/refresh-cache")
    public Result<Void> refreshCache() {
        statsCacheService.refreshAllStats();
        return Result.success();
    }
}

