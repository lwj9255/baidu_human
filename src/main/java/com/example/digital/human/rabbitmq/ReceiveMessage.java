package com.example.digital.human.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.digital.human.consts.BaiDuConstant;
import com.example.digital.human.pojo.Parameters;
import com.example.digital.human.pojo.RequestResult;
import com.example.digital.human.pojo.WebSocketMessage;
import com.example.digital.human.service.AgentMessageService;
import com.example.digital.human.websocket.handler.MyWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

//消费者
@Service
public class ReceiveMessage {
    // 临时存储未形成完整句子的片段
    private StringBuilder contentCache = new StringBuilder();

    @RabbitListener(queues = {"bootDirectQueue"})
    public void fanoutReceive(Message message) {
        // 重置状态变量
        contentCache.setLength(0);          // 每次监听先清空缓存
        // 用数组是因为Lambda 中访问的局部变量是不可重新赋值的，但是数组引用不变，因此可以变动里面的内容
        final boolean[] flagFirst = {true}; // 是否第一句
        final String[] previousSentence = {null}; // 缓存上一句，实现错峰发送

        byte[] body = message.getBody();
        String messageBody = new String(body, StandardCharsets.UTF_8);
        System.out.println("接收消息：" + messageBody);

        Parameters parameters = new Parameters();
        parameters.setInput(messageBody);
        RequestResult requestResult = new RequestResult();
        requestResult.setParameters(parameters);
        String jsonInputString = JSON.toJSONString(requestResult);

        getAgentMessageStreaming1("https://api.coze.cn/v1/workflow/stream_run?workflow_id=7534995985676714024",
                jsonInputString,
                chunk -> {
                    if (previousSentence[0] == null) {
                        // 缓存第一句，不发送
                        previousSentence[0] = chunk;
                        return;
                    }

                    // 发送上一句（非最后句，last=false）
                    WebSocketMessage wsMsgPrev = new WebSocketMessage(previousSentence[0], false, flagFirst[0]);
                    System.out.println(JSON.toJSONString(wsMsgPrev));
                    MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgPrev));
                    flagFirst[0] = false;

                    // 更新缓存为当前句
                    previousSentence[0] = chunk;
                });

        // 流结束，发送最后剩余句，last=true
        flushCache(previousSentence, flagFirst);
    }

    // 流结束调用，发送最后一句
    private void flushCache(String[] previousSentence, boolean[] flagFirst) {
        if (previousSentence[0] != null) {
            // previousSentence最后一句是否last取决于contentCache是否为空
            boolean isLast = contentCache.length() == 0;
            WebSocketMessage wsMsgLast = new WebSocketMessage(previousSentence[0], isLast, flagFirst[0]);
            System.out.println(JSON.toJSONString(wsMsgLast));
            MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgLast));
            previousSentence[0] = null;
        } else {
            System.out.println("没有剩余句子可发送");
        }

        String remain = contentCache.toString().trim();
        if (!remain.isEmpty()) {
            WebSocketMessage wsMsgRemain = new WebSocketMessage(remain, true, flagFirst[0]);
            System.out.println(JSON.toJSONString(wsMsgRemain));
            MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgRemain));
            contentCache.setLength(0);
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

            // 发送请求数据
            out = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            out.write(data);
            out.flush();

            // 获取输入流
            InputStream is = conn.getInputStream();
            br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            char[] buffer = new char[2];// 临时存放读到的字符
            int charsRead;
            StringBuilder contentBuffer = new StringBuilder();// 把每次读到的字符拼起来

            while ((charsRead = br.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, charsRead);// 把buffer里的字符转化为字符串
                contentBuffer.append(chunk);

                int jsonStart = contentBuffer.indexOf("{");
                int jsonEnd = contentBuffer.lastIndexOf("}");

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String completeJson = contentBuffer.substring(jsonStart, jsonEnd + 1);
                    processAndSendJson(completeJson, chunkConsumer);
                    contentBuffer.delete(0, jsonEnd + 1);
                }
            }

            if (contentBuffer.length() > 0) {
                String leftoverNoWhitespace = contentBuffer.toString().replaceAll("\\s+", "");
                if (!leftoverNoWhitespace.isEmpty()) {
                    processAndSendJson(contentBuffer.toString(), chunkConsumer);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            chunkConsumer.accept("{\"error\":\"" + e.getMessage() + "\"}");
        } finally {
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
        System.out.println("收到的原始json字符串: " + jsonStr);
        try {
            long leftBraceCount = jsonStr.chars().filter(ch -> ch == '{').count();
            long rightBraceCount = jsonStr.chars().filter(ch -> ch == '}').count();

            if (leftBraceCount > rightBraceCount) {
                // 补充缺失的右大括号
                StringBuilder sb = new StringBuilder(jsonStr);
                for (long i = 0; i < leftBraceCount - rightBraceCount; i++) {
                    sb.append('}');
                }
                jsonStr = sb.toString();
                System.out.println("自动补全后的json字符串: " + jsonStr);
            }
            JSONObject json = JSON.parseObject(jsonStr);

            if (json.containsKey("content")) {
                String content = json.getString("content");
                if (content != null) {
                    String normalizedContent = content.replaceAll("[\\r\\n]+", "").trim();

                    System.out.println("原始content: [" + content + "]");
                    System.out.println("normalizedContent: [" + normalizedContent + "]");
                    System.out.println("追加前contentCache内容: [" + contentCache.toString() + "]");

                    if (!normalizedContent.isEmpty()) {
                        contentCache.append(normalizedContent);
                        System.out.println("合并后contentCache内容: [" + contentCache.toString() + "]");

                        String contentStr = contentCache.toString();
                        int lastPeriodIndex = contentStr.lastIndexOf("。");// 找这次json里最后一个句号的位置

                        if (lastPeriodIndex != -1) {
                            String sendPart = contentStr.substring(0, lastPeriodIndex + 1);
                            String remainPart = contentStr.substring(lastPeriodIndex + 1);
                            System.out.println("sendPart: [" + sendPart + "]");
                            System.out.println("remainPart: [" + remainPart + "]");

                            String[] sentences = sendPart.split("。");// 万一某次json中有多个句号，所以以句号拆分一下
                            for (String sentence : sentences) {
                                sentence = sentence.trim();
                                if (!sentence.isEmpty()) {
                                    System.out.println("发送句子: [" + sentence + "。]");
                                    chunkConsumer.accept(sentence + "。");
                                }
                            }

                            contentCache.setLength(0);
                            contentCache.append(remainPart.trim());
                            System.out.println("拆句后，contentCache重置为: [" + contentCache.toString() + "]");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析JSON出错: " + jsonStr + e.getMessage());
            e.printStackTrace();
        }
    }
}
