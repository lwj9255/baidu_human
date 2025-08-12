package com.example.digital.human;

import com.example.digital.human.rabbitmq.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DigitalHumanApplicationTests {

    @Autowired
    private MessageService messageService;

    @Test
    public void testSendMessage() {
        String testMsg = "介绍一下南瑞集团";
//        String testMsg = "讲一个以感叹号结尾的长故事";
        messageService.sendMessage(testMsg);
        System.out.println("消息已发送: " + testMsg);
    }

}
