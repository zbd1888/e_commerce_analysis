package com.example.ecommerce.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    private static final int MAX_ROUNDS = 5;
    private static final Set<String> ADMIN_ONLY_TOOLS = Set.of(
            "get_admin_monitor_stats",
            "get_clean_logs",
            "get_running_clean_tasks",
            "get_user_management_stats",
            "get_crawler_status"
    );

    private String systemPrompt;
    private List<Map<String, Object>> tools;

    @PostConstruct
    public void init() {
        this.systemPrompt = buildSystemPrompt();
        this.tools = buildTools();
    }

    public String chat(String userMessage) {
        return chat(userMessage, "user");
    }

    public String chat(String userMessage, String role) {
        if (!isApiKeyConfigured()) {
            return "⚠️ AI 服务未配置，请设置 DeepSeek API Key（环境变量 DEEPSEEK_API_KEY 或 application.yml 中的 deepseek.api-key）。";
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt + buildRolePolicy(role)));
        messages.add(Map.of("role", "user", "content", userMessage));

        for (int round = 0; round < MAX_ROUNDS; round++) {
            Map<String, Object> response = callDeepSeek(messages);

            List<Map<String, Object>> choices = extractChoices(response);
            if (choices == null || choices.isEmpty()) break;

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> assistantMsg = extractMessage(choice);
            String finishReason = extractFinishReason(choice);

            if (assistantMsg == null) break;
            messages.add(assistantMsg);

            if ("stop".equals(finishReason)) {
                String content = (String) assistantMsg.get("content");
                return (content != null && !content.isBlank()) ? content : "已收到您的请求，但无法生成有效回答。";
            }

            if ("tool_calls".equals(finishReason)) {
                List<Map<String, Object>> toolCalls = extractToolCalls(assistantMsg);
                if (toolCalls == null || toolCalls.isEmpty()) continue;

                // content 可能和 tool_calls 同时出现，若有文本也先追加
                String content = (String) assistantMsg.get("content");
                if (content != null && !content.isBlank()) {
                    log.debug("AI 在调用工具的同时输出文本: {}", content);
                }

                for (Map<String, Object> toolCall : toolCalls) {
                    String callId = (String) toolCall.get("id");
                    Map<String, Object> function = extractFunction(toolCall);
                    if (function == null) continue;

                    String name = (String) function.get("name");
                    String args = (String) function.get("arguments");

                    log.info("AI 调用工具: {} args: {}", name, args);
                    String result = executeTool(name, args, role);

                    Map<String, Object> toolResult = new HashMap<>();
                    toolResult.put("role", "tool");
                    toolResult.put("tool_call_id", callId);
                    toolResult.put("content", result);
                    messages.add(toolResult);
                }
                // 继续下一轮推理
                continue;
            }

            // other finish reasons: length, content_filter, etc.
            String content = (String) assistantMsg.get("content");
            if (content != null && !content.isBlank()) {
                return content;
            }
            break;
        }

        return "分析过程过于复杂，请简化问题后重试。";
    }

    // ========== DeepSeek API 调用 ==========

    private Map<String, Object> callDeepSeek(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("tools", tools);
            body.put("temperature", 0.7);
            body.put("max_tokens", 2048);

            String jsonBody = objectMapper.writeValueAsString(body);

            String responseStr = cn.hutool.http.HttpRequest.post(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .timeout(120000)
                    .execute()
                    .body();

            if (responseStr == null || responseStr.isBlank()) {
                throw new RuntimeException("DeepSeek API 返回空响应");
            }

            return objectMapper.readValue(responseStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("调用 DeepSeek API 失败: {}", e.getMessage());
            throw new RuntimeException("AI 服务暂时不可用，请稍后重试。（" + e.getMessage() + "）");
        }
    }

    // ========== 工具执行 ==========

    private String executeTool(String name, String argumentsJson, String role) {
        try {
            Map<String, Object> args = parseArgs(argumentsJson);

            if (ADMIN_ONLY_TOOLS.contains(name) && !"admin".equals(role)) {
                return "{\"error\": \"Admin permission required\"}";
            }

            return switch (name) {
                case "get_blue_ocean_categories" ->
                        toJson(analysisService.getBlueOceanCategories());
                case "get_red_ocean_categories" ->
                        toJson(analysisService.getRedOceanCategories());
                case "get_category_potential_ranking" ->
                        toJson(analysisService.getCategoryPotentialRanking());
                case "get_market_supply_demand" ->
                        toJson(analysisService.getMarketSupplyDemand());
                case "get_hot_product_features" -> {
                    String kw = strArg(args, "keyword");
                    yield toJson(analysisService.getHotProductFeatures(kw));
                }
                case "get_price_strategy" ->
                        toJson(analysisService.getPriceStrategy());
                case "comprehensive_prediction" -> {
                    String kw = strArg(args, "keyword");
                    double price = numArg(args, "price");
                    String loc = strArg(args, "location");
                    String tag = strArg(args, "shopTag");
                    String title = strArg(args, "title");
                    yield toJson(analysisService.comprehensivePrediction(kw, price, loc, tag, title));
                }
                case "get_store_ranking" ->
                        toJson(analysisService.getStoreRanking());
                default -> "{\"error\": \"未知工具: " + name + "\"}";
            };
        } catch (Exception e) {
            log.error("执行工具 {} 失败", name, e);
            return "{\"error\": \"工具执行失败: " + e.getMessage() + "\"}";
        }
    }

    // ========== System Prompt ==========

    private String buildSystemPrompt() {
        return """
                你是电商选品分析专家，拥有分析淘宝/天猫数据的工具。

                ## 你的核心能力
                - 分析品类的市场潜力和竞争程度（蓝海/红海识别）
                - 识别各品类爆款特征（价格区间、店铺标签、发货地）
                - 预测不同价格下的销量表现
                - 给出品类选择建议和定价策略
                - 多维度综合评估选品成功率

                ## 回答规则
                1. 必须给出具体数字（价格、销量、成功率），不要只说"很好"、"不错"
                2. 基于工具返回的真实数据做判断
                3. 按以下结构输出：
                   【核心结论】一句话总结
                   【数据支撑】关键数据表格或要点
                   【行动建议】可操作的建议
                4. 超过 3 个数据点时用表格展示
                5. 如果用户问的是"推荐品类"，优先查看蓝海品类 + 品类潜力排行
                6. 如果问具体品类（如"母婴"），先看供需分析，再看价格策略，然后看爆款特征
                7. 回答简洁专业，用中文
                """;
    }

    // ========== 工具定义 ==========

    private String buildRolePolicy(String role) {
        String safeRole = role == null || role.isBlank() ? "user" : role;
        return """

                ## Access Control
                Current user role: %s.
                - Regular users may only access product, category, price, sales prediction, and store ranking analysis.
                - Regular users must not access admin module data, crawler status, recent crawl counts, cleaning logs, user management, system monitoring, or database connection details.
                - If a regular user asks for admin-only data, refuse and explain that admin permission is required. Do not guess or fabricate numbers.
                - Only the admin role may answer admin module questions.
                """.formatted(safeRole);
    }

    private List<Map<String, Object>> buildTools() {
        List<Map<String, Object>> toolList = new ArrayList<>();

        toolList.add(tool(
                "get_blue_ocean_categories",
                "获取蓝海品类列表（高热度低竞争，适合新手入场）",
                obj(
                        prop("type", "object"),
                        prop("properties", obj()),
                        prop("required", list())
                )
        ));

        toolList.add(tool(
                "get_red_ocean_categories",
                "获取红海品类列表（高竞争，不适合新手）",
                obj(
                        prop("type", "object"),
                        prop("properties", obj()),
                        prop("required", list())
                )
        ));

        toolList.add(tool(
                "get_category_potential_ranking",
                "获取品类综合潜力排行榜，包含热度分、竞争分、综合评分",
                obj(
                        prop("type", "object"),
                        prop("properties", obj()),
                        prop("required", list())
                )
        ));

        toolList.add(tool(
                "get_market_supply_demand",
                "获取各品类市场供需分析数据，包含供需比和竞争指数",
                obj(
                        prop("type", "object"),
                        prop("properties", obj()),
                        prop("required", list())
                )
        ));

        toolList.add(tool(
                "get_hot_product_features",
                "获取某品类的爆款特征分析（价格区间、均价、平均销量、主流店铺标签、发货地）",
                obj(
                        prop("type", "object"),
                        prop("properties", obj(
                                prop("keyword", obj(
                                        prop("type", "string"),
                                        prop("description", "品类关键词，如'母婴'、'女装'，空字符串返回全部")
                                ))
                        )),
                        prop("required", list("keyword"))
                )
        ));

        toolList.add(tool(
                "get_price_strategy",
                "获取各品类最优价格区间和定价策略建议",
                obj(
                        prop("type", "object"),
                        prop("properties", obj()),
                        prop("required", list())
                )
        ));

        toolList.add(tool(
                "comprehensive_prediction",
                "综合预测 - 根据品类、价格、发货地、店铺标签、标题，评估选品成功率和各维度评分",
                obj(
                        prop("type", "object"),
                        prop("properties", obj(
                                prop("keyword", obj(
                                        prop("type", "string"),
                                        prop("description", "品类关键词，必填")
                                )),
                                prop("price", obj(
                                        prop("type", "number"),
                                        prop("description", "商品价格，必填")
                                )),
                                prop("location", obj(
                                        prop("type", "string"),
                                        prop("description", "发货地，如'广东 深圳'（可选）")
                                )),
                                prop("shopTag", obj(
                                        prop("type", "string"),
                                        prop("description", "店铺标签，如'品牌旗舰店'、'官方旗舰店'（可选）")
                                )),
                                prop("title", obj(
                                        prop("type", "string"),
                                        prop("description", "商品标题（可选）")
                                ))
                        )),
                        prop("required", list("keyword", "price"))
                )
        ));

        toolList.add(tool(
                "get_store_ranking",
                "获取店铺销量排行榜",
                obj(
                        prop("type", "object"),
                        prop("properties", obj()),
                        prop("required", list())
                )
        ));

        return toolList;
    }

    // ========== 辅助方法 ==========

    private boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.contains("your-key");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractChoices(Map<String, Object> response) {
        var choices = response.get("choices");
        if (choices instanceof List) {
            return (List<Map<String, Object>>) choices;
        }
        // 可能的错误响应格式
        if (response.containsKey("error")) {
            Object err = response.get("error");
            log.error("DeepSeek API 返回错误: {}", err);
            throw new RuntimeException("DeepSeek API 错误: " + err);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMessage(Map<String, Object> choice) {
        Object msg = choice.get("message");
        return msg instanceof Map ? (Map<String, Object>) msg : null;
    }

    private String extractFinishReason(Map<String, Object> choice) {
        Object reason = choice.get("finish_reason");
        return reason instanceof String ? (String) reason : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> message) {
        Object calls = message.get("tool_calls");
        return calls instanceof List ? (List<Map<String, Object>>) calls : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFunction(Map<String, Object> toolCall) {
        Object func = toolCall.get("function");
        return func instanceof Map ? (Map<String, Object>) func : null;
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析工具参数失败: {}", json, e);
            return new HashMap<>();
        }
    }

    private String strArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? v.toString() : "";
    }

    private double numArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\": \"序列化失败\"}";
        }
    }

    // ========== 工具构建辅助 ==========

    @SafeVarargs
    private static <T> List<T> list(T... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static Map<String, Object> obj(Map.Entry<String, Object>... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (var e : entries) map.put(e.getKey(), e.getValue());
        return map;
    }

    private static Map.Entry<String, Object> prop(String key, Object value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("type", "function");
        toolDef.put("function", function);
        return toolDef;
    }
}
