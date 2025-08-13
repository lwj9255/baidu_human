package com.example.digital.human;

import com.example.digital.human.rabbitmq.MessageService;
import com.example.digital.human.websocket.handler.MyWebSocketHandler;
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

//    @Test
//    public void testSendMessage1() {
//        for (int i = 0; i < 100000; i++) {
//            try {
//                // 暂停10秒（10000毫秒）
//                String aa="{\"first\":true,\"last\":false,\"message\":\"南瑞集团有限公司（以下简称“南瑞集团”）是国家电网有限公司直属科研产业单位，是我国能源电力及工业控制领域卓越的IT企业，以下为你详细介绍：### 发展历程- 1973年，水利电力部南京自动化研究所成立，是南瑞集团的前身。\"}";
//                MyWebSocketHandler.sendMessageToAll(aa);
//                String bb="{\"first\":false,\"last\":false,\"message\":\"- 2001年，由南京自动化研究院整体转制组建南瑞集团有限公司。\"}";
//                MyWebSocketHandler.sendMessageToAll(bb);
//                String cc="{\"first\":false,\"last\":true,\"message\":\"同时，在工业控制领域，为冶金、石化、建材等多个行业提供自动化控制系统和技术服务。\"}";
//                MyWebSocketHandler.sendMessageToAll(cc);
//                Thread.sleep(4000);
//                System.out.println("-----111");
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//    }

}
