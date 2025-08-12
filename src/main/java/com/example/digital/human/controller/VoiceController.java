package com.example.digital.human.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.digital.human.consts.BaiDuConstant;
import com.example.digital.human.pojo.Parameters;
import com.example.digital.human.pojo.RequestResult;
import com.example.digital.human.rabbitmq.MessageService;
import com.example.digital.human.service.AgentMessageService;
import com.example.digital.human.service.VoiceTranslationService;
import com.example.digital.human.websocket.handler.MyWebSocketHandler;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {
    @Resource
    private AgentMessageService agentMessageService;
    @Resource
    private VoiceTranslationService translationService;
    @Resource
    private MessageService messageService;

    /**
     * 获取上传的语音文件
     *
     * @param voiceFile
     * @return
     */
    @PostMapping("/upload")
    public String getVoice(@RequestParam("voiceFile") MultipartFile voiceFile) {
        try {
            // 获取语音数据
            byte[] voiceData = voiceFile.getBytes();
            if (voiceData.length > 0) {
                // 处理语音...
                translationService.getText(voiceFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "上传失败";
        }
        return "上传成功";
    }

    /**
     * 测试agent接口
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

    /**
     * 测试agent接口
     *
     * @return
     */
    @PostMapping("/getAnswerResult1")
    public void getAnswerResult1() throws IOException {
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
        // 使用流式处理方式将消息传给agent并实时推送到前端
        getAgentMessageStreaming1("https://api.coze.cn/v1/workflow/stream_run?workflow_id=7534995985676714024", jsonInputString, chunk -> {
            Date date2 = new Date();
            System.out.println("MyWebSocketHandler------->" + format.format(date2));
            System.out.println("实时消息片段：" + chunk);
            if (StringUtils.isNotBlank(chunk)) {
                MyWebSocketHandler.sendMessageToAll(chunk);
            }
            Date date3 = new Date();
            System.out.println("MyWebSocketHandler------->" + format.format(date3));
        });
        Date date1 = new Date();
        System.out.println("格式化后的时间------->" + format.format(date1));
    }

    @PostMapping("/getAnswerResult2")
    public void getAnswerResult2() throws IOException {
        // 创建要发送的JSON对象
        messageService.sendMessage("介绍一下南瑞集团");

    }
    public void getAgentMessageStreaming(String pathUrl, String data, Consumer<String> chunkConsumer) {
        try {
            System.out.println("-1---"+new Date());
            URL url = new URL(pathUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setRequestProperty("Authorization", BaiDuConstant.AUTHORIZATION);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setChunkedStreamingMode(0);
            System.out.println("-2---"+new Date());
            // 发送请求
            try (OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                out.write(data);
                out.flush();
            }
            System.out.println("-3---"+new Date());
            // 流式读取响应
            try (InputStream is = conn.getInputStream()) {
                System.out.println("-4---"+new Date());
                byte[] buffer = new byte[1024]; // 缓冲区大小可调整
                int bytesRead;
                StringBuilder chunkBuilder = new StringBuilder();
                System.out.println("-5---"+new Date());
                while ((bytesRead = is.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    chunkBuilder.append(chunk);

                    // 简单按换行符分割（根据Coze实际数据格式调整）
                    int splitIndex;
                    while ((splitIndex = chunkBuilder.indexOf("\n")) >= 0) {
                        String line = chunkBuilder.substring(0, splitIndex);
                        chunkConsumer.accept(line); // 处理单个数据块
                        chunkBuilder.delete(0, splitIndex + 1);
                    }
                }
                System.out.println("-6---"+new Date());
                // 处理剩余数据
                if (chunkBuilder.length() > 0) {
                    chunkConsumer.accept(chunkBuilder.toString());
                }
                System.out.println("-7---"+new Date());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void getAgentMessageStreaming1(String pathUrl, String data, Consumer<String> chunkConsumer) {
        HttpURLConnection conn = null;
        OutputStreamWriter out = null;
        BufferedReader br = null;

        try {
            URL url = new URL(pathUrl);
            conn = (HttpURLConnection) url.openConnection();

            // 配置连接
            conn.setRequestMethod("POST");
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setRequestProperty("Authorization", BaiDuConstant.AUTHORIZATION);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // conn.setChunkedStreamingMode(200); // 可以考虑移除或增大这个值

            // 发送请求数据
            out = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            out.write(data);
            out.flush();

            // 获取输入流
            InputStream is = conn.getInputStream();
            br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            char[] buffer = new char[2]; // 使用更大的缓冲区
            int charsRead;
            StringBuilder contentBuffer = new StringBuilder();

            while ((charsRead = br.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, charsRead);
                contentBuffer.append(chunk);

                // 尝试找到JSON边界
                int jsonStart = contentBuffer.indexOf("{");
                int jsonEnd = contentBuffer.lastIndexOf("}");

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String completeJson = contentBuffer.substring(jsonStart, jsonEnd + 1);
                    processAndSendJson(completeJson, chunkConsumer);
                    contentBuffer.delete(0, jsonEnd + 1);
                }
            }

            // 处理剩余内容
            if (contentBuffer.length() > 0) {
                chunkConsumer.accept(contentBuffer.toString());
            }

        } catch (IOException e) {
            e.printStackTrace();
            chunkConsumer.accept("{\"error\":\"" + e.getMessage() + "\"}");
        } finally {
            // 确保按照正确顺序关闭资源
            try {
                if (br != null) br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (out != null) out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (conn != null) conn.disconnect();
        }
    }

    private void processAndSendJson(String jsonStr, Consumer<String> chunkConsumer) {
        try {
            // 尝试解析为JSON
            JSONObject json = JSON.parseObject(jsonStr);

            // 如果包含content字段，提取内容
            if (json.containsKey("content")) {
                String content = json.getString("content");
                if (content != null && !content.isEmpty()) {
                    // 如果是嵌套JSON，继续解析
                    if (content.startsWith("{") && content.endsWith("}")) {
                        JSONObject contentObj = JSON.parseObject(content);
                        if (contentObj.containsKey("output")) {
                            chunkConsumer.accept(contentObj.getString("output"));
                            return;
                        }
                    }
                    // 直接发送content内容
                    chunkConsumer.accept(content);
                    return;
                }
            }

            // 默认发送整个JSON
            chunkConsumer.accept(jsonStr);
        } catch (Exception e) {
            // 解析失败，直接发送原始字符串
            chunkConsumer.accept(jsonStr);
        }
    }
}
