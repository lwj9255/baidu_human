package com.example.digital.human.controller;

import com.example.digital.human.rabbitmq.MessageService;
import com.example.digital.human.websocket.handler.MyWebSocketHandler;
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

    private volatile boolean running = false;

    @GetMapping("/start")
    public String startSending() {
        if (running) {
            return "循环已经在运行中";
        }
        running = true;
        new Thread(() -> {
            String[] messages = {
                    "{\"first\":true,\"last\":false,\"message\":\"南瑞集团有限公司（以下简称“南瑞集团”）是国家电网有限公司直属科研产业单位，是我国能源电力及工业控制领域卓越的IT企业，以下为你详细介绍：### 发展历程- 1973年，水利电力部南京自动化研究所成立，是南瑞集团的前身。\"}",
                    "{\"first\":false,\"last\":false,\"message\":\"- 2001年，由南京自动化研究院整体转制组建南瑞集团有限公司。\"}",
                    "{\"first\":false,\"last\":true,\"message\":\"同时，在工业控制领域，为冶金、石化、建材等多个行业提供自动化控制系统和技术服务。\"}"
            };

            try {
                for (int i = 0; i < 100000 && running; i++) {
                    if (MyWebSocketHandler.hasOpenSession()) {
                        for (String msg : messages) {
                            if (!running) break; // 如果被停止，立即退出
                            MyWebSocketHandler.sendMessageToAll(msg);
                            Thread.sleep(3000);
                        }
                        System.out.println("一轮发送完成");
                    } else {
                        System.out.println("无客户端连接，等待中...");
                    }
                    Thread.sleep(4000); // 每轮间隔 4 秒
                }
            } catch (InterruptedException e) {
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
