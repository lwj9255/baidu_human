package com.example.digital.human.controller;

import com.alibaba.fastjson.JSON;
import com.example.digital.human.pojo.Parameters;
import com.example.digital.human.pojo.RequestResult;
import com.example.digital.human.rabbitmq.MessageService;
import com.example.digital.human.service.AgentMessageService;
import com.example.digital.human.websocket.handler.MyWebSocketHandler;
import jakarta.annotation.Resource;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 测试用的接口
 */
@RestController
@RequestMapping("/api/message")
public class MessageControllerTest {

    @Resource
    private MessageService messageService;
    @Resource
    private AgentMessageService agentMessageService;

    /**
     * 测试agent回答问题
     *
     * @return
     */
    @GetMapping("/send")
    public String sendMessage() {
        String testMsg = "介绍一下南瑞集团";
        messageService.sendMessage(testMsg);
        System.out.println("消息已发送: " + testMsg);
        return "success";
    }

    /**
     * 测试agent回答问题
     *
     * @return
     */
    @PostMapping("/getAnswerResult")
    public String getAnswerResult() throws IOException {
        // 创建要发送的JSON对象
        Parameters parameters = new Parameters();
        parameters.setInput("介绍一下南瑞集团");
        RequestResult requestResult = new RequestResult();
        requestResult.setParameters(parameters);
        String jsonInputString = JSON.toJSONString(requestResult);
        System.out.println("====" + parameters.getInput());
        //使用Date创建日期对象
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("格式化后的时间------->" + format.format(date));
        String agent = agentMessageService.getAgentMessage("https://api.coze.cn/v1/workflow/stream_run?workflow_id=7534995985676714024", jsonInputString);
        Date date1 = new Date();
        System.out.println("格式化后的时间------->" + format.format(date1));
        // 获取agent答案
        return agent;
    }

    private volatile boolean running = false;

    /**
     * 测试图片表格发送给c#
     *
     * @return
     */
    @GetMapping("/start")
    public String startSending() {
        if (running) {
            return "循环已经在运行中";
        }
        running = true;
        new Thread(() -> {
            String[] messages = {
                    "{\"first\":false,\"last\":false,\"message\":\"介绍一下南瑞集团\",\"type\":\"0\"}",
                    "{\"first\":true,\"last\":true,\"message\":\"您好，我正在为您检索相关信息，请稍等片刻。\",\"type\":\"1\"}",
                    "{\"first\":true,\"last\":false,\"message\":\"它在能源电力及工业控制领域颇具影响力。\",\"type\":\"1\"}",
                    "{\"first\":false,\"last\":false,\"message\":\"南瑞技术实力强，拥有大量科研人员，在电网自动化、继电保护等多个关键技术领域成果丰硕。\",\"type\":\"1\"}",
                    "{\"first\":false,\"last\":true,\"message\":\"业务广泛，涵盖电网自动化、电力信息通信、发电及水利自动化等板块。\",\"type\":\"1\"}"
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
                                        MyWebSocketHandler.sendBinaryToAll(imageBytes, 2);
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
                                        MyWebSocketHandler.sendBinaryToAll(imageBytes, 2);
                                        System.out.println("发送图片，大小: " + imageBytes.length + " 字节");
                                    } else {
                                        System.out.println("未找到 test.png");
                                    }
                                }
                                Thread.sleep(1000);

                                try (InputStream is = getClass().getClassLoader().getResourceAsStream("static/test.csv")) {
                                    if (is != null) {
                                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                                        String headerLine = reader.readLine();
                                        if (headerLine != null) {
                                            String[] headers = headerLine.split(",");
                                            List<Map<String, String>> tableData = new ArrayList<>();
                                            String line;
                                            while ((line = reader.readLine()) != null) {
                                                String[] values = line.split(",");
                                                Map<String, String> row = new LinkedHashMap<>();
                                                for (int k = 0; k < headers.length && k < values.length; k++) {
                                                    row.put(headers[k], values[k]);
                                                }
                                                tableData.add(row);
                                            }
                                            String jsonTable = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tableData);
                                            String csvmsg = String.format("{\"first\":false,\"last\":true,\"message\":%s,\"type\":\"3\"}", jsonTable);
                                            MyWebSocketHandler.sendMessageToAll(csvmsg);
                                            System.out.println("发送表格: " + jsonTable);
                                        }
                                    } else {
                                        System.out.println("未找到 test.csv");
                                    }
                                }
                                Thread.sleep(1000);
                                // 使用示例
                                String csvString = "\uFEFF,姓名,年龄,城市\n1,张三,25,北京\n2,李四,30,上海";
                                processCsvFromString(csvString);
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

    //将字符串转化为表格
    public void processCsvFromString(String csvData) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(csvData));

            String headerLine = reader.readLine();
            if (headerLine != null) {
                String[] headers = headerLine.split(",");
                List<Map<String, String>> tableData = new ArrayList<>();
                String line;

                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",");
                    Map<String, String> row = new LinkedHashMap<>();

                    for (int k = 0; k < headers.length && k < values.length; k++) {
                        row.put(headers[k], values[k]);
                    }
                    tableData.add(row);
                }

                String jsonTable = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tableData);
                String csvmsg = String.format("{\"first\":false,\"last\":true,\"message\":%s,\"type\":\"3\"}", jsonTable);
                MyWebSocketHandler.sendMessageToAll(csvmsg);
                System.out.println("发送表格1: " + jsonTable);

            }
        } catch (Exception e) {
            System.out.println("处理CSV数据时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
