package com.zziony.Davinci;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class DavinciController {

    @GetMapping("/davinci")
    public String davinci() {
        return "오징어 게임을 시작합니다.";
    }

}
