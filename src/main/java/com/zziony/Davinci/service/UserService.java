package com.zziony.Davinci.service;

import com.zziony.Davinci.model.User;
import com.zziony.Davinci.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Qualifier("UserRepository")
    private final UserRepository userRepository;

    @Autowired  // 필드 주입 대신 생성자 주입 추천
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String registerUser(String nickname) {
        // 닉네임 중복 체크
        Optional<User> existingUser = userRepository.findByNickname(nickname);
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        // 새로운 유저 생성
        User user = new User(nickname);
        userRepository.save(user);
        return user.getSessionId();
    }

    public Optional<User> getUserBySessionId(String sessionId) {
        return userRepository.findBySessionId(sessionId);
    }
}
