package com.example.ecommerce.controller;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.config.AuthInterceptor;
import com.example.ecommerce.service.AiAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI 智能助手")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;

    @Operation(summary = "AI 对话 - 发送消息获取智能选品建议")
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.error("消息不能为空");
        }
        try {
            String role = (String) request.getAttribute(AuthInterceptor.ATTR_ROLE);
            String reply = aiAssistantService.chat(message, role);
            return Result.success(Map.of("reply", reply));
        } catch (Exception e) {
            return Result.error("AI 服务暂时不可用: " + e.getMessage());
        }
    }

    @Operation(summary = "健康检查 - 查看 AI 服务状态")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "status", "running",
                "model", "deepseek-v4-flash"
        ));
    }
}
