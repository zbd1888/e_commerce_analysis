package com.example.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ecommerce.common.Result;
import com.example.ecommerce.entity.CleanLog;
import com.example.ecommerce.entity.HotProductRule;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.service.AnalysisService;
import com.example.ecommerce.service.CleanLogService;
import com.example.ecommerce.service.HotProductRuleService;
import com.example.ecommerce.service.ProductService;
import com.example.ecommerce.service.StatsCacheService;
import com.example.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Tag(name = "管理员功能")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CleanLogService cleanLogService;
    private final ProductService productService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final StatsCacheService statsCacheService;
    private final HotProductRuleService hotProductRuleService;
    private final AnalysisService analysisService;
    private final DataSource dataSource;

    // 应用启动时间（用于计算运行时长）
    private static final Instant APP_START_TIME = Instant.now();

    // 辅助方法：扫描Excel文件并转换为Map列表
    private List<Map<String, Object>> scanExcelFiles(java.util.function.Function<File, Map<String, Object>> mapper, String sortKey) {
        File dir = getCrawlerDirectory();
        if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".xlsx"));
        if (files == null) return new ArrayList<>();
        List<Map<String, Object>> result = Arrays.stream(files).map(mapper).collect(java.util.stream.Collectors.toList());
        result.sort((a, b) -> ((Date)b.get(sortKey)).compareTo((Date)a.get(sortKey)));
        return result;
    }

    @Operation(summary = "获取爬虫数据文件列表")
    @GetMapping("/files/list")
    public Result<List<Map<String, Object>>> getFileList() {
        return Result.success(scanExcelFiles(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", f.getName()); m.put("path", f.getAbsolutePath());
            m.put("size", f.length()); m.put("lastModified", new Date(f.lastModified()));
            return m;
        }, "lastModified"));
    }

    @Operation(summary = "获取清洗日志列表")
    @GetMapping("/clean/logs")
    public Result<List<CleanLog>> getCleanLogs(
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(cleanLogService.getRecentLogs(limit));
    }

    @Operation(summary = "获取清洗日志详情")
    @GetMapping("/clean/log/{batchId}")
    public Result<CleanLog> getCleanLogDetail(@PathVariable String batchId) {
        return Result.success(cleanLogService.getByBatchId(batchId));
    }

    @Operation(summary = "获取运行中的清洗任务")
    @GetMapping("/clean/running")
    public Result<List<CleanLog>> getRunningTasks() {
        return Result.success(cleanLogService.getRunningTasks());
    }

    @Operation(summary = "系统监控统计")
    @GetMapping("/monitor/stats")
    public Result<Map<String, Object>> getMonitorStats() {
        Map<String, Object> stats = new HashMap<>();

        // 获取最近清洗记录数
        List<CleanLog> recentLogs = cleanLogService.getRecentLogs(10);
        stats.put("recentCleanLogs", recentLogs.size());

        // 计算总处理记录数
        int totalProcessed = recentLogs.stream()
                .mapToInt(log -> log.getTotalCount() != null ? log.getTotalCount() : 0)
                .sum();
        stats.put("totalProcessedRecords", totalProcessed);

        // 运行中任务数
        stats.put("runningTasks", cleanLogService.getRunningTasks().size());

        return Result.success(stats);
    }

    @Operation(summary = "管理员大屏统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getAdminStats() {
        // 从缓存获取统计数据
        Map<String, Object> stats = statsCacheService.getAdminStats();

        // 待清洗文件数（实时计算，因为文件数量变化频繁）
        File dir = getCrawlerDirectory();
        int pendingFiles = 0;
        if (dir.exists() && dir.isDirectory()) {
            File[] excelFiles = dir.listFiles((d, name) -> name.endsWith(".xlsx"));
            pendingFiles = excelFiles != null ? excelFiles.length : 0;
        }
        stats.put("pendingFiles", pendingFiles);
        return Result.success(stats);
    }

    @Operation(summary = "刷新统计缓存（清洗入库后调用）")
    @PostMapping("/stats/refresh")
    public Result<Void> refreshStats() {
        statsCacheService.refreshAllStats();
        return Result.success();
    }

    @Operation(summary = "分页获取清洗日志")
    @GetMapping("/clean-logs")
    public Result<Page<CleanLog>> getCleanLogsPaged(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<CleanLog> pageResult = cleanLogService.page(new Page<>(page, size),
                new LambdaQueryWrapper<CleanLog>().orderByDesc(CleanLog::getCreatedAt));
        return Result.success(pageResult);
    }

    @Operation(summary = "数据质量统计（从缓存读取，毫秒级响应）")
    @GetMapping("/quality")
    public Result<Map<String, Object>> getDataQuality() {
        Map<String, Object> quality = statsCacheService.getDataQuality();
        return Result.success(quality);
    }

    @Operation(summary = "获取待清洗文件列表")
    @GetMapping("/pending-files")
    public Result<List<Map<String, Object>>> getPendingFiles() {
        return Result.success(scanExcelFiles(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", f.getName()); m.put("size", formatFileSize(f.length())); m.put("modifiedTime", new Date(f.lastModified()));
            return m;
        }, "modifiedTime"));
    }

    /**
     * 获取爬虫目录 - 尝试多个可能的路径
     */
    private File getCrawlerDirectory() {
        // 优先尝试：当前目录的父目录下的"爬虫"文件夹（从ecommerce-analysis目录启动）
        String userDir = System.getProperty("user.dir");
        File dir = new File(userDir, "../爬虫");
        if (dir.exists() && dir.isDirectory()) {
            return dir;
        }

        // 备选1：当前目录下的"爬虫"文件夹（从项目根目录启动）
        dir = new File(userDir, "爬虫");
        if (dir.exists() && dir.isDirectory()) {
            return dir;
        }

        // 备选2：固定路径（Windows开发环境）
        dir = new File("D:/code/java/爬虫");
        if (dir.exists() && dir.isDirectory()) {
            return dir;
        }

        // 返回默认路径（即使不存在）
        return new File(userDir, "../爬虫");
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024));
    }

    /**
     * 探测爬虫Python服务(localhost:5000)是否存活
     */
    private Map<String, Object> getCrawlerServiceStatus() {
        Map<String, Object> info = new HashMap<>();
        try {
            URL url = new URL("http://localhost:5000/api/crawler/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // 读取响应获取运行中的任务数
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    // 简单解析JSON中的runningTasks字段
                    String body = sb.toString();
                    int runningTasks = 0;
                    int idx = body.indexOf("\"runningTasks\":");
                    if (idx >= 0) {
                        String num = body.substring(idx + 15).replaceAll("[^0-9]", " ").trim().split("\\s+")[0];
                        runningTasks = Integer.parseInt(num);
                    }
                    info.put("status", "running");
                    info.put("runningTasks", runningTasks);
                }
            } else {
                info.put("status", "stopped");
                info.put("runningTasks", 0);
            }
            conn.disconnect();
        } catch (Exception e) {
            // 连接失败 = 爬虫服务未启动
            log.debug("爬虫服务探测失败(服务可能未启动): {}", e.getMessage());
            info.put("status", "stopped");
            info.put("runningTasks", 0);
        }
        return info;
    }

    /**
     * 获取数据库真实状态信息
     */
    private Map<String, Object> getDatabaseStatus() {
        Map<String, Object> dbInfo = new HashMap<>();
        try {
            // 获取HikariCP连接池信息
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDS = (HikariDataSource) dataSource;
                HikariPoolMXBean poolMXBean = hikariDS.getHikariPoolMXBean();
                if (poolMXBean != null) {
                    dbInfo.put("activeConnections", poolMXBean.getActiveConnections());
                    dbInfo.put("idleConnections", poolMXBean.getIdleConnections());
                    dbInfo.put("totalConnections", poolMXBean.getTotalConnections());
                } else {
                    dbInfo.put("activeConnections", hikariDS.getMaximumPoolSize());
                }
            } else {
                dbInfo.put("activeConnections", "N/A");
            }

            // 查询数据库表数量
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                if (rs.next()) {
                    dbInfo.put("tableCount", rs.getInt(1));
                }
            }
            dbInfo.put("status", "connected");
        } catch (Exception e) {
            log.error("获取数据库状态失败", e);
            dbInfo.put("status", "error");
            dbInfo.put("activeConnections", 0);
            dbInfo.put("tableCount", 0);
        }
        return dbInfo;
    }

    @Operation(summary = "分页获取用户列表")
    @GetMapping("/users")
    public Result<Page<User>> getUserList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<User> pageResult = userService.page(new Page<>(page, size),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));
        // 清除密码字段
        pageResult.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(pageResult);
    }

    @Operation(summary = "添加用户")
    @PostMapping("/users")
    public Result<Void> addUser(@RequestBody User user) {
        // 检查用户名是否已存在
        if (userService.getByUsername(user.getUsername()) != null) {
            return Result.error("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        userService.save(user);
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "修改用户角色")
    @PutMapping("/users/{id}/role")
    public Result<Void> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userService.getById(id);
        if (user != null) {
            user.setRole(body.get("role"));
            userService.updateById(user);
        }
        return Result.success();
    }

    @Operation(summary = "修改用户状态")
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        User user = userService.getById(id);
        if (user != null) {
            user.setStatus(body.get("status"));
            userService.updateById(user);
        }
        return Result.success();
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "批量删除商品")
    @PostMapping("/products/batch-delete")
    public Result<Void> batchDeleteProducts(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null && !ids.isEmpty()) {
            productService.removeByIds(ids);
        }
        return Result.success();
    }

    @Operation(summary = "系统监控状态")
    @GetMapping("/monitor/status")
    public Result<Map<String, Object>> getMonitorStatus(@RequestParam(defaultValue = "false") Boolean refresh) {
        Map<String, Object> status = new HashMap<>();

        // 后端服务状态 - 计算真实运行时长
        status.put("backendStatus", "running");
        Duration uptime = Duration.between(APP_START_TIME, Instant.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        status.put("backendUptime", hours > 0 ? hours + "小时" + minutes + "分钟" : minutes + "分钟");

        // 爬虫服务状态 - 真正探测 Python 爬虫服务(localhost:5000)是否存活
        Map<String, Object> crawlerInfo = getCrawlerServiceStatus();
        status.put("crawlerStatus", crawlerInfo.get("status"));
        status.put("crawlerRunningTasks", crawlerInfo.get("runningTasks"));

        // 最后爬取时间（从清洗日志获取）
        List<CleanLog> recentLogs = cleanLogService.getRecentLogs(1);
        if (!recentLogs.isEmpty()) {
            CleanLog lastLog = recentLogs.get(0);
            status.put("lastCrawlTime", lastLog.getFinishedAt() != null ?
                    lastLog.getFinishedAt().toString() : lastLog.getCreatedAt().toString());
        } else {
            status.put("lastCrawlTime", "暂无记录");
        }

        // 数据库状态 - 获取真实连接池信息
        Map<String, Object> dbInfo = getDatabaseStatus();
        status.put("dbStatus", dbInfo.get("status"));
        status.put("dbConnections", dbInfo.get("activeConnections"));
        status.put("dbTableCount", dbInfo.get("tableCount"));

        // 数据统计：如果刷新参数为true，则先刷新缓存再获取
        if (Boolean.TRUE.equals(refresh)) {
            statsCacheService.refreshAllStats();
        }
        Map<String, Object> adminStats = statsCacheService.getAdminStats();
        status.put("totalProducts", adminStats.getOrDefault("totalProducts", 0));
        status.put("todayNewProducts", adminStats.getOrDefault("todayNewProducts", 0));
        status.put("validProducts", adminStats.getOrDefault("validProducts", 0));

        // 清洗成功率
        Map<String, Object> quality = statsCacheService.getDataQuality();
        status.put("cleanSuccessRate", quality.getOrDefault("completeness", 0));

        // 最近任务
        status.put("recentTasks", cleanLogService.getRecentLogs(10).stream().map(log -> {
            Map<String, Object> t = new HashMap<>();
            t.put("keyword", log.getKeyword());
            t.put("status", "SUCCESS".equals(log.getStatus()) ? "completed" : "RUNNING".equals(log.getStatus()) ? "running" : "failed");
            t.put("totalCount", log.getTotalCount());
            t.put("successCount", log.getInsertedCount() + log.getUpdatedCount());
            t.put("failCount", log.getSkippedCount());
            t.put("startTime", log.getStartedAt() != null ? log.getStartedAt().toString() : "-");
            t.put("endTime", log.getFinishedAt() != null ? log.getFinishedAt().toString() : "-");
            t.put("duration", log.getStartedAt() != null && log.getFinishedAt() != null
                    ? java.time.Duration.between(log.getStartedAt(), log.getFinishedAt()).getSeconds() + "秒" : "-");
            return t;
        }).toList());

        return Result.success(status);
    }

    @Operation(summary = "获取爆品规则列表")
    @GetMapping("/rule/list")
    public Result<List<HotProductRule>> getRuleList() {
        return Result.success(hotProductRuleService.getAllRules());
    }

    @Operation(summary = "保存爆品规则")
    @PostMapping("/rule/save")
    public Result<Void> saveRule(@RequestBody HotProductRule rule) {
        log.info("保存爆品规则: {}", rule);
        hotProductRuleService.saveOrUpdateRule(rule);
        // 异步刷新爆品相关缓存，确保全局和品类数据一致性
        refreshHotProductCacheAsync();
        return Result.success();
    }

    @Operation(summary = "删除爆品规则")
    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        log.info("删除爆品规则: {}", id);
        hotProductRuleService.removeById(id);
        // 异步刷新爆品相关缓存
        refreshHotProductCacheAsync();
        return Result.success();
    }

    @Operation(summary = "切换爆品规则状态")
    @PutMapping("/rule/{id}/toggle")
    public Result<Void> toggleRuleStatus(@PathVariable Long id) {
        hotProductRuleService.toggleStatus(id);
        // 异步刷新爆品相关缓存
        refreshHotProductCacheAsync();
        return Result.success();
    }

    /**
     * 异步刷新爆品相关缓存，避免阻塞接口响应
     */
    private void refreshHotProductCacheAsync() {
        new Thread(() -> {
            try {
                log.info("开始异步刷新爆品缓存...");
                analysisService.refreshHotProductCache();
                log.info("爆品缓存异步刷新完成");
            } catch (Exception e) {
                log.error("异步刷新爆品缓存失败", e);
            }
        }).start();
    }

    @Operation(summary = "获取启用的爆品规则")
    @GetMapping("/rule/enabled")
    public Result<List<HotProductRule>> getEnabledRules() {
        return Result.success(hotProductRuleService.getEnabledRules());
    }

    @Operation(summary = "根据品类获取适用的规则")
    @GetMapping("/rule/by-keyword")
    public Result<List<HotProductRule>> getRulesByKeyword(@RequestParam(required = false) String keyword) {
        return Result.success(hotProductRuleService.getRulesByKeyword(keyword));
    }
}

