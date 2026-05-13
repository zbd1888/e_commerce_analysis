package com.example.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ecommerce.dto.LoginDTO;
import com.example.ecommerce.dto.LoginVO;
import com.example.ecommerce.dto.RegisterDTO;
import com.example.ecommerce.entity.User;

public interface UserService extends IService<User> {
    LoginVO login(LoginDTO dto);
    void register(RegisterDTO dto);
    User getByUsername(String username);
}
