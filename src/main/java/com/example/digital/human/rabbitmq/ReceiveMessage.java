package com.example.digital.human.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.digital.human.consts.BaiDuConstant;
import com.example.digital.human.pojo.Parameters;
import com.example.digital.human.pojo.RequestResult;
import com.example.digital.human.pojo.WebSocketMessage;
import com.example.digital.human.websocket.handler.MyWebSocketHandler;
import jakarta.annotation.PreDestroy;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class ReceiveMessage {

    // 单线程线程池，保证顺序且可中断
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 当前正在运行的任务Future引用，用AtomicReference作为对象引用容器保证线程安全
    private final AtomicReference<Future<?>> currentTask = new AtomicReference<>();

    /**
     * MQ监听方法，提交异步任务执行，接收到新消息时取消上一个任务
     */
    @RabbitListener(queues = {"bootDirectQueue"})
    public void fanoutReceive(Message message) {
        // getAndSet(newvalue) 原子地执行：取出当前存放的值，把存放的值换成newvalue
        Future<?> previousFuture = currentTask.getAndSet(null);
        // 如果上一个任务存在且没完成，取消并发送结束msg给前端
        if (previousFuture != null && !previousFuture.isDone()) {
            System.out.println("检测到新消息，取消旧任务");
            previousFuture.cancel(true);  // 发送中断信号给旧任务
            WebSocketMessage wsMsg = new WebSocketMessage("", true, false);
            MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsg));
        }
        // 以关闭旧websocket通道为信号，提示前端需要中断上次任务
        MyWebSocketHandler.closeAllSessions();

        // 创建新任务对象，内容是调用processMessage(message)
        Runnable task = () -> processMessage(message);
        // 将任务提交到线程池
        Future<?> future = executor.submit(task);
        currentTask.set(future);
    }

    /**
     * 处理单条消息的完整逻辑
     * @param message MQ消息
     */
    private void processMessage(Message message) {
        // 内容缓存，单线程环境下可以保证线程安全
        StringBuilder contentCache = new StringBuilder();

        // 是否第一句
        final boolean[] flagFirst = {true};
        // 保存上一句，错峰发送，方便找到最后一句话
        final String[] previousSentence = {null};

        try {
            byte[] body = message.getBody();
            String messageBody = new String(body, StandardCharsets.UTF_8);
            System.out.println("接收消息：" + messageBody);

            Parameters parameters = new Parameters();
            parameters.setInput(messageBody);
            RequestResult requestResult = new RequestResult();
            requestResult.setParameters(parameters);
            String jsonInputString = JSON.toJSONString(requestResult);

            // 调用流式请求方法，传入contentCache和chunk处理器
            getAgentMessageStreaming1(
                    "https://api.coze.cn/v1/workflow/stream_run?workflow_id=7534995985676714024",
                    jsonInputString,
                    chunk -> {
                        // 任务处理中检测线程是否被中断
                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("任务被中断，停止发送数据");
                            return;
                        }
                        // 按原逻辑处理分块数据
                        if (previousSentence[0] == null) {
                            previousSentence[0] = chunk;
                            return;
                        }
                        WebSocketMessage wsMsgPrev = new WebSocketMessage(previousSentence[0], false, flagFirst[0]);
                        System.out.println(JSON.toJSONString(wsMsgPrev));
                        MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgPrev));
                        flagFirst[0] = false;
                        previousSentence[0] = chunk;
                    },
                    contentCache
            );

            flushCache(previousSentence, flagFirst, contentCache);

        } catch (Exception e) {
            System.err.println("处理消息异常：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 支持中断的流式请求处理方法
     * @param pathUrl 请求URL
     * @param data POST数据
     * @param chunkConsumer 接收数据块回调
     * @param contentCache 内容缓存
     */
    public void getAgentMessageStreaming1(String pathUrl, String data, Consumer<String> chunkConsumer, StringBuilder contentCache) throws IOException {
        HttpURLConnection conn = null;
        OutputStreamWriter out = null;
        BufferedReader br = null;

        try {
            URL url = new URL(pathUrl);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setRequestProperty("Authorization", BaiDuConstant.AUTHORIZATION);
            conn.setDoOutput(true);
            conn.setDoInput(true);

            out = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            out.write(data);
            out.flush();

            InputStream is = conn.getInputStream();
            br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            char[] buffer = new char[2];
            int charsRead;
            StringBuilder contentBuffer = new StringBuilder();

            while ((charsRead = br.read(buffer)) != -1) {
                // 检测线程中断，及时退出
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("HTTP读取被中断，退出");
                    break;
                }

                String chunk = new String(buffer, 0, charsRead);
                contentBuffer.append(chunk);

                int jsonStart = contentBuffer.indexOf("{");
                int jsonEnd = contentBuffer.lastIndexOf("}");

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String completeJson = contentBuffer.substring(jsonStart, jsonEnd + 1);
                    processAndSendJson(completeJson, chunkConsumer, contentCache);
                    contentBuffer.delete(0, jsonEnd + 1);
                }
            }

            if (contentBuffer.length() > 0) {
                String leftoverNoWhitespace = contentBuffer.toString().replaceAll("\\s+", "");
                if (!leftoverNoWhitespace.isEmpty()) {
                    processAndSendJson(contentBuffer.toString(), chunkConsumer, contentCache);
                }
            }
        } finally {
            if (br != null) br.close();
            if (out != null) out.close();
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 处理并发送JSON内容，和之前逻辑类似
     */
    private void processAndSendJson(String jsonStr, Consumer<String> chunkConsumer, StringBuilder contentCache) {
//        System.out.println("收到的原始json字符串: " + jsonStr);
        try {
            long leftBraceCount = jsonStr.chars().filter(ch -> ch == '{').count();
            long rightBraceCount = jsonStr.chars().filter(ch -> ch == '}').count();

            if (leftBraceCount > rightBraceCount) {
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

                    if (!normalizedContent.isEmpty()) {
                        contentCache.append(normalizedContent);

                        String contentStr = contentCache.toString();
                        int lastPeriodIndex = contentStr.lastIndexOf("。");

                        if (lastPeriodIndex != -1) {
                            String sendPart = contentStr.substring(0, lastPeriodIndex + 1);
                            String remainPart = contentStr.substring(lastPeriodIndex + 1);

                            String[] sentences = sendPart.split("。");
                            for (String sentence : sentences) {
                                sentence = sentence.trim();
                                if (!sentence.isEmpty()) {
                                    chunkConsumer.accept(sentence + "。");
                                }
                            }

                            contentCache.setLength(0);
                            contentCache.append(remainPart.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析JSON出错: " + jsonStr + " " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 发送最后剩余缓存内容
     */
    private void flushCache(String[] previousSentence, boolean[] flagFirst, StringBuilder contentCache) {
        // 若最后第二句话不是空（一般来说不可能最后两句话都是空的，但有时候是空的）
        if (previousSentence[0] != null) {
            // 若缓存中是空的，那么最后第二句就是最后一句，last=false
            boolean isLast = contentCache.length() == 0;
            WebSocketMessage wsMsgLast = new WebSocketMessage(previousSentence[0], isLast, flagFirst[0]);
            System.out.println(JSON.toJSONString(wsMsgLast));
            MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgLast));
            previousSentence[0] = null;
        }
        // 若缓存中不是空
        String remain = contentCache.toString().trim();
        if (!remain.isEmpty()) {
            // 缓存中的句子是最后一句话
            WebSocketMessage wsMsgRemain = new WebSocketMessage(remain, true, flagFirst[0]);
            System.out.println(JSON.toJSONString(wsMsgRemain));
            MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgRemain));
            contentCache.setLength(0);
        }
    }
}