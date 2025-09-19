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
            WebSocketMessage wsMsg = new WebSocketMessage("", false, true, 1);
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
        // 标记本轮是否正常结束（收到 node_is_finish:true）
        final AtomicBoolean normalFinish = new AtomicBoolean(false);
        // 标记本轮是否被取消/中断（线程被打断）
        final AtomicBoolean aborted = new AtomicBoolean(false);

        // 是否已经发送过任意一包（用于保证“首包一定 first:true”）
        final AtomicBoolean hasSentAny = new AtomicBoolean(false);
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
                WebSocketMessage question = new WebSocketMessage(
                        messageBody,
                        false,
                        false,
                        0
                );
                MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(question));
                System.out.println("发送问题：" + messageBody);
            } else {
                System.out.println("警告: 等待新连接超时，第一句可能会丢失");
            }

            Parameters parameters = new Parameters();
            parameters.setInput(messageBody);
            RequestResult requestResult = new RequestResult();
            requestResult.setParameters(parameters);
            String jsonInputString = JSON.toJSONString(requestResult);

            // 调用流式请求方法，传入contentCache和chunk处理器
            getAgentMessageStreaming(
                    "https://api.coze.cn/v1/workflow/stream_run?workflow_id=7537961634657566747",
                    jsonInputString,
                    chunk -> {
                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("任务被中断，停止发送数据");
                            aborted.set(true);
                            return;
                        }
                        if (previousSentence[0] == null) {
                            previousSentence[0] = chunk;
                            return;
                        }
                        boolean isFirstSend = !hasSentAny.get();
                        WebSocketMessage wsMsgPrev = new WebSocketMessage(previousSentence[0], isFirstSend, false, 1);
                        System.out.println(JSON.toJSONString(wsMsgPrev));
                        MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgPrev));
                        hasSentAny.set(true);

                        previousSentence[0] = chunk;
                    },
                    contentCache,
                    normalFinish,
                    aborted
            );

            // 若本轮已被中断（比如下一轮问题来了），不要再把残句 flush 出去
            if (aborted.get()) {
                System.out.println("本轮已中断，跳过 flushCache 以避免半句落地");
                return;
            }
            flushCache(previousSentence, hasSentAny, contentCache, normalFinish);
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
    public void getAgentMessageStreaming(String pathUrl, String data,
                                         Consumer<String> chunkConsumer,
                                         StringBuilder contentCache,
                                         AtomicBoolean normalFinish,
                                         AtomicBoolean aborted) throws IOException{
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
                    aborted.set(true);
                    break;
                }

                String chunk = new String(buffer, 0, charsRead);
                contentBuffer.append(chunk);

                int jsonStart = contentBuffer.indexOf("{");
                int jsonEnd = contentBuffer.lastIndexOf("}");

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String completeJson = contentBuffer.substring(jsonStart, jsonEnd + 1);
                    processAndSendJson(completeJson, chunkConsumer, contentCache, normalFinish);
                    contentBuffer.delete(0, jsonEnd + 1);
                }
            }

            if (contentBuffer.length() > 0) {
                String leftoverNoWhitespace = contentBuffer.toString().replaceAll("\\s+", "");
                if (!leftoverNoWhitespace.isEmpty()) {
                    processAndSendJson(contentBuffer.toString(), chunkConsumer, contentCache, normalFinish);
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
    private void processAndSendJson(String jsonStr,
                                    Consumer<String> chunkConsumer,
                                    StringBuilder contentCache,
                                    AtomicBoolean normalFinish){
        System.out.println("收到的原始json字符串: " + jsonStr);
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
            // 标记是否正常结束（兼容你日志里的字段）
            if (json.containsKey("node_is_finish")) {
                try {
                    Boolean fin = json.getBoolean("node_is_finish");
                    if (Boolean.TRUE.equals(fin)) {
                        normalFinish.set(true);
                    }
                } catch (Exception ignore) {}
            }
            if (json.containsKey("content")) {
                String content = json.getString("content");
                if (content != null) {
                    // 保留换行，用作潜在句末；仅去掉装饰符
                    String normalizedContent = content.replaceAll("[#*]+", "").trim();

                    if (!normalizedContent.isEmpty()) {
                        contentCache.append(normalizedContent);

                        String contentStr = contentCache.toString();

                        // 允许的句末标点集合
                        String endings = "。！？!?；;：:\n\r";
                        int lastEndIdx = -1;
                        for (int i = contentStr.length() - 1; i >= 0; i--) {
                            if (endings.indexOf(contentStr.charAt(i)) >= 0) {
                                lastEndIdx = i;
                                break;
                            }
                        }

                        if (lastEndIdx >= 0) {
                            String sendPart = contentStr.substring(0, lastEndIdx + 1);
                            String remainPart = contentStr.substring(lastEndIdx + 1);

                            // 以句末标点切分，保留标点（基于后行断言）
                            String[] sentences = sendPart.split("(?<=[。！？\n\r])");
                            for (String sentence : sentences) {
                                sentence = sentence.trim();
                                if (!sentence.isEmpty()) {
                                    chunkConsumer.accept(sentence);
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
    private void flushCache(String[] previousSentence,
                            AtomicBoolean hasSentAny,
                            StringBuilder contentCache,
                            AtomicBoolean normalFinish) {
        String remain = contentCache.toString().trim();

        // 句末标点集合
        String endings = "。！？!?；;：:";
        boolean remainHasEnding = !remain.isEmpty() && endings.indexOf(remain.charAt(remain.length() - 1)) >= 0;

        // 情况A：有上一句缓存（上一句是完整句，还没发出去）
        if (previousSentence[0] != null) {
            // 正常结束 且 没有剩余 -> 上一句就是最后一句
            // 异常结束 且 有残句但未到句末 -> 仍然把上一句作为 last:true（丢弃半句）
            boolean forceLast =
                    (remain.isEmpty()) || (!normalFinish.get() && !remainHasEnding);

            boolean isFirstSend = !hasSentAny.get();
            WebSocketMessage wsMsgLast = new WebSocketMessage(previousSentence[0], isFirstSend, forceLast, 1);
            System.out.println(JSON.toJSONString(wsMsgLast));
            MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgLast));
            hasSentAny.set(true);

            // 如果已经作为最后一句发出，并且是不正常结束导致的半句，则后面的半句直接丢弃
            if (forceLast && !normalFinish.get() && !remain.isEmpty() && !remainHasEnding) {
                System.out.println("异常结束：丢弃未完半句 => " + remain);
                contentCache.setLength(0);
                return;
            }
        }

        // 情况B：还有剩余（可能是完整句或半句）
        if (!remain.isEmpty()) {
            // 正常结束：若无句末标点，补一个句号使其读起来完整
            if (normalFinish.get() && !remainHasEnding) {
                remain = remain + "。";
            }

            // 异常结束：只在“已经有句末标点”的前提下发出剩余；否则丢弃
            if (!normalFinish.get() && !remainHasEnding) {
                System.out.println("异常结束：丢弃未完半句 => " + remain);
                contentCache.setLength(0);
                return;
            }

            boolean isFirstSend = !hasSentAny.get();
            WebSocketMessage wsMsgRemain = new WebSocketMessage(remain, isFirstSend, true, 1);
            System.out.println(JSON.toJSONString(wsMsgRemain));
            MyWebSocketHandler.sendMessageToAll(JSON.toJSONString(wsMsgRemain));
            hasSentAny.set(true);
            contentCache.setLength(0);
        }
    }
}