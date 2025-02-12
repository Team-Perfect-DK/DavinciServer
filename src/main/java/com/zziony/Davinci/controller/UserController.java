package com.zziony.Davinci.controller;

import com.zziony.Davinci.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "http://localhost:3000")  // 프론트엔드 주소에 맞춰 CORS 설정
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Map<String, String> registerUser(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");
        String sessionId = userService.registerUser(nickname);

        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        return response;
    }
}
