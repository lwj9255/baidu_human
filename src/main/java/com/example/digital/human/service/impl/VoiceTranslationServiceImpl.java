package com.example.digital.human.service.impl;

import com.alibaba.fastjson.JSON;
import com.example.digital.human.rabbitmq.MessageService;
import com.example.digital.human.service.VoiceTranslationService;
import jakarta.annotation.Resource;
import jakarta.xml.bind.DatatypeConverter;
import okhttp3.*;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class VoiceTranslationServiceImpl implements VoiceTranslationService {
    public static final String API_KEY = "bce-v3/ALTAK-PHa4FJkT1VDEZKj6oDnjn/81ceb86aabc275642c728f7ad38b857758748feb";
    public static final String SECRET_KEY = "WygwzfmTBWeDFORnNhVIE8N9ECUuJtRw";

    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder().readTimeout(300, TimeUnit.SECONDS).build();
    @Resource
    private MessageService messageService;

    /**
     * 将语音转换为汉字
     *
     * @param voiceFile
     * @return
     * @throws IOException
     */
    public String getText(MultipartFile voiceFile) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");

        byte[] voiceData = voiceFile.getBytes();
        long fileSize = voiceData.length;
//         speech 可以通过 getFileContentAsBase64("C:\fakepath\16k.wav") 方法获取,如果Content-Type是application/x-www-form-urlencoded时,第二个参数传true
        String speech = DatatypeConverter.printBase64Binary(voiceFile.getBytes());
//        long fileSize =129600l;
//        String speech=getFileContentAsBase64("C:\\fakepath\\16k.pcm",false);

        String json = String.format("{\"format\":\"pcm\",\"rate\":16000,\"channel\":1," +
                        "\"cuid\":\"jpWZtRhK7dfE9Aq8HNay3EJ98AopzQtX\",\"dev_pid\":80001," +
                        "\"speech\":\"%s\",\"len\":%d,\"token\":\"24.aeea6e0ab7daad7ee3ff285a4da078c7.2592000.1756898832.282335-119689951\"}",
                speech, fileSize);
//        RequestBody body = RequestBody.create(mediaType, "{\"format\":\"pcm\",\"rate\":16000,\"channel\":1,\"cuid\":\"jpWZtRhK7dfE9Aq8HNay3EJ98AopzQtX\",\"dev_pid\":80001,\"speech\":\"" + aaa + "\",\"len\":%d,\"token\":\"24.aeea6e0ab7daad7ee3ff285a4da078c7.2592000.1756898832.282335-119689951\"}");
        RequestBody body = RequestBody.create(mediaType, json);
        Request request = new Request.Builder()
                .url("https://vop.baidu.com/pro_api")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();
        Response response = HTTP_CLIENT.newCall(request).execute();
        String voiceResult = response.body().string();
//        java字符串转JSON对象
        com.alibaba.fastjson.JSONObject jsonUrlObject = JSON.parseObject(voiceResult);
        String resultStr = jsonUrlObject.getString("result");
        String[] resultArr = resultStr.split("\"");
        System.out.println(resultArr[1]);
        messageService.sendMessage(resultArr[1]);
        return voiceResult;
    }

    /**
     * 获取文件base64编码
     *
     * @param path      文件路径
     * @param urlEncode 如果Content-Type是application/x-www-form-urlencoded时,传true
     * @return base64编码信息，不带文件头
     * @throws IOException IO异常
     */
    public String getFileContentAsBase64(String path, boolean urlEncode) throws IOException {
        byte[] b = Files.readAllBytes(Paths.get(path));
        String base64 = Base64.getEncoder().encodeToString(b);
        if (urlEncode) {
            base64 = URLEncoder.encode(base64, "utf-8");
        }
        return base64;
    }

    /**
     * 从用户的AK，SK生成鉴权签名（Access Token）
     *
     * @return 鉴权签名（Access Token）
     * @throws IOException IO异常
     */
    public String getAccessToken() throws IOException {
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, "grant_type=client_credentials&client_id=" + API_KEY
                + "&client_secret=" + SECRET_KEY);
        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token")
                .method("POST", body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        Response response = HTTP_CLIENT.newCall(request).execute();
        return new JSONObject(response.body().string()).getString("access_token");
    }
}
