package com.example.ecommerce.dto;

import lombok.Data;

@Data
public class LoginVO {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String token;
}
