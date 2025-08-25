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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class ReceiveMessage {

    // 单线程线程池，保证顺序且可中断
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // AtomicReference作为对象引用容器保证线程安全:因为他是原子地读/写/交换
    private final AtomicReference<Future<?>> currentTask = new AtomicReference<>();

    long startTime;

    boolean firstJsonflag = true;

    /**
     * MQ监听方法，提交异步任务执行，接收到新消息时取消上一个任务
     */
    @RabbitListener(queues = {"bootDirectQueue"})
    public void fanoutReceive(Message message) {
        firstJsonflag = true;
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
     *
     * @param message MQ消息
     */
    private void processMessage(Message message) {
        // 内容缓存，单线程环境下可以保证线程安全
        StringBuilder contentCache = new StringBuilder();

        // 是否第一句
        final boolean[] flagFirst = {false};
        // 保存上一句，错峰发送，方便找到最后一句话
        final String[] previousSentence = {null};
        // 创建一个带预定功能的单线程线程池
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//        final AtomicBoolean greetingSent = new AtomicBoolean(false);

        startTime = System.currentTimeMillis(); // 记录开始时间

        try {
            byte[] body = message.getBody();
            String messageBody = new String(body, StandardCharsets.UTF_8);
            System.out.println("接收消息：" + messageBody);

            // 等待新的 WebSocket 连接建立（最多等 5 秒）
            boolean connectedEnough = false;
            for (int i = 0; i < 50; i++) { // 每次 sleep 100ms，最多等 5 秒
                int sessionCount = MyWebSocketHandler.getOpenSessionCount();
                if (sessionCount >= 2) {
                    connectedEnough = true;
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 如果超时仍未连接，可以选择直接发（可能会丢）或者跳过
            if (connectedEnough || MyWebSocketHandler.getOpenSessionCount() > 0) {
                MyWebSocketHandler.sendMessageToAll(messageBody);
                System.out.println("发送问题："+messageBody);
            } else {
                System.out.println("警告: 等待新连接超时，第一句可能会丢失");
            }

            ScheduledFuture<?> greetingFuture = scheduler.schedule(() -> {
                if (previousSentence[0] == null) { // 2秒内没有第一句话
                    // 准备欢迎语数组
                    String[] greetings = new String[]{
                            "您好，我正在为您检索相关信息，请稍等片刻。",
                            "您好，请稍等，我正在为您处理请求。",
                            "我正在为您搜集相关内容，请耐心等待。",
                            "您好，请稍等，我正在努力获取相关信息。",
                            "您好，正在快速为你整理答案，请耐心等待。",
                            "您好，我正在查找相关资料，请稍等一下。",
                            "您好，请耐心等待，我正在努力获取数据。",
                            "您好，正在为您检索内容，请稍候片刻。"
                    };
                    // 随机选择一句
                    int index = ThreadLocalRandom.current().nextInt(greetings.length);
                    String selectedGreeting = greetings[index];

                    WebSocketMessage greeting = new WebSocketMessage(
                            selectedGreeting,
                            true,
                            true
                    );
                    MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(greeting));
//                    greetingSent.set(true);
                }
            }, 2, TimeUnit.SECONDS);

            Parameters parameters = new Parameters();
            parameters.setInput(messageBody);
            RequestResult requestResult = new RequestResult();
            requestResult.setParameters(parameters);
            String jsonInputString = JSON.toJSONString(requestResult);

            // 调用流式请求方法，传入contentCache和chunk处理器
            getAgentMessageStreaming1(
                    "https://api.coze.cn/v1/workflow/stream_run?workflow_id=7537961634657566747",
                    jsonInputString,
                    chunk -> {
                        // 任务处理中检测线程是否被中断
                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("任务被中断，停止发送数据");
                            return;
                        }
                        // 上一句话存着不发
                        if (previousSentence[0] == null) {
                            previousSentence[0] = chunk;
                            flagFirst[0] = true;

                            // 记录第一个句子到达时间
//                            long firstSentenceTime = System.currentTimeMillis();
//                            long delayMs = firstSentenceTime - startTime;
//                            System.out.println("第一句话到达延迟: " + delayMs + "ms");

//                            if (!greetingSent.get()) {
//                                greetingFuture.cancel(false);
//                            }

                            return;
                        }
                        WebSocketMessage wsMsgPrev = new WebSocketMessage(previousSentence[0], false, flagFirst[0]);
                        System.out.println(JSON.toJSONString(wsMsgPrev));
                        MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgPrev));
                        flagFirst[0] = false;
                        previousSentence[0] = chunk;
//                        if (firstJsonflag) {
//                            long firstSentenceTime = System.currentTimeMillis();
//                            long delayMs = firstSentenceTime - startTime;
//                            System.out.println("第一个json到达延迟: " + delayMs + "ms");
//                            firstJsonflag = false;
//                        }
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
     *
     * @param pathUrl       请求URL
     * @param data          POST数据
     * @param chunkConsumer 接收数据块回调
     * @param contentCache  内容缓存
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

            char[] buffer = new char[128];
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
//            previousSentence[0] = null;
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