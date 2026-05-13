package com.example.ecommerce.config;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USERNAME = "currentUsername";
    public static final String ATTR_ROLE = "currentUserRole";

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractBearerToken(request.getHeader("Authorization"));
        if (token == null || !jwtUtils.validateToken(token)) {
            writeError(response, 401, "Unauthorized or token expired");
            return false;
        }

        Claims claims = jwtUtils.parseToken(token);
        String role = claims.get("role", String.class);

        request.setAttribute(ATTR_USER_ID, claims.get("userId", Long.class));
        request.setAttribute(ATTR_USERNAME, claims.getSubject());
        request.setAttribute(ATTR_ROLE, role);

        if (request.getRequestURI().startsWith("/api/admin/") && !"admin".equals(role)) {
            writeError(response, 403, "Admin permission required");
            return false;
        }

        return true;
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String prefix = "Bearer ";
        if (authorization.startsWith(prefix)) {
            return authorization.substring(prefix.length()).trim();
        }
        return authorization.trim();
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
