package com.example.digital.human.controller;

import com.example.digital.human.rabbitmq.MessageService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/message")
public class MessageControllerTest {

    @Resource
    private MessageService messageService;

    @GetMapping("/send")
    public String sendMessage() {
        String testMsg = "介绍一下南瑞集团";
//        String testMsg = "讲一个以感叹号结尾的长故事";
        messageService.sendMessage(testMsg);
        System.out.println("消息已发送: " + testMsg);
        return "success";
    }

}
