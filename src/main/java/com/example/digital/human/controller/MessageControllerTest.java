package com.example.digital.human.controller;

import com.example.digital.human.rabbitmq.MessageService;
import com.example.digital.human.websocket.handler.MyWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;


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

    private volatile boolean running = false;

    @GetMapping("/start")
    public String startSending() {
        if (running) {
            return "循环已经在运行中";
        }
        running = true;
        new Thread(() -> {
            String[] messages = {
                    "介绍一下南瑞集团",
                    "{\"first\":true,\"last\":true,\"message\":\"您好，我正在为您检索相关信息，请稍等片刻。\"}",
                    "{\"first\":true,\"last\":false,\"message\":\"它在能源电力及工业控制领域颇具影响力。\"}",
                    "{\"first\":false,\"last\":false,\"message\":\"南瑞技术实力强，拥有大量科研人员，在电网自动化、继电保护等多个关键技术领域成果丰硕。\"}",
                    "{\"first\":false,\"last\":true,\"message\":\"业务广泛，涵盖电网自动化、电力信息通信、发电及水利自动化等板块。\"}"
            };

            try {
                for (int i = 0; i < 100000 && running; i++) {
                    if (MyWebSocketHandler.hasOpenSession()) {
                        for (int j = 0; j < messages.length && running; j++) {
                            String msg = messages[j];
                            MyWebSocketHandler.sendMessageToAll(msg);
                            System.out.println("发送文本: " + msg);
                            Thread.sleep(1000);

                            // 在第二条消息之后插入一张图片
                            if (j == 1) {
                                try (InputStream is = getClass().getClassLoader().getResourceAsStream("static/test.png")) {
                                    if (is != null) {
                                        byte[] imageBytes = is.readAllBytes();
                                        MyWebSocketHandler.sendBinaryToAll(imageBytes);
                                        System.out.println("发送图片，大小: " + imageBytes.length + " 字节");
                                    } else {
                                        System.out.println("未找到 test.png");
                                    }
                                }
                                Thread.sleep(1000);
                            }
                            if (j == 3) {
                                try (InputStream is = getClass().getClassLoader().getResourceAsStream("static/test1.png")) {
                                    if (is != null) {
                                        byte[] imageBytes = is.readAllBytes();
                                        MyWebSocketHandler.sendBinaryToAll(imageBytes);
                                        System.out.println("发送图片，大小: " + imageBytes.length + " 字节");
                                    } else {
                                        System.out.println("未找到 test.png");
                                    }
                                }
                                Thread.sleep(1000);
                            }
                        }
                        System.out.println("一轮发送完成");
                    } else {
                        System.out.println("无客户端连接，等待中...");
                    }
                    Thread.sleep(4000); // 每轮间隔 4 秒
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                running = false;
            }
        }).start();
        return "循环已启动";
    }

    /**
     * 停止循环发送
     */
    @GetMapping("/stop")
    public String stopSending() {
        running = false;
        return "循环已停止";
    }

}
