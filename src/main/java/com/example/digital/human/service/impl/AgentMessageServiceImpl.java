package com.example.digital.human.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.digital.human.consts.BaiDuConstant;
import com.example.digital.human.pojo.ResponseResult;
import com.example.digital.human.rabbitmq.MessageService;
import com.example.digital.human.service.AgentMessageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentMessageServiceImpl implements AgentMessageService {
    @Resource
    private MessageService messageService;
    //回答结果

    /**
     * 以post或get方式调用对方接口方法，
     *
     * @param pathUrl
     */
    public String getAgentMessage(String pathUrl, String data) {
        OutputStreamWriter out = null;
        BufferedReader br = null;
        String result = "";
        String jsonInputString = "";
        try {
            URL url = new URL(pathUrl);
            //打开和url之间的连接
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            //请求方式
            conn.setRequestMethod("POST");
            //conn.setRequestMethod("GET");

            //设置通用的请求属性
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setRequestProperty("Authorization", BaiDuConstant.AUTHORIZATION);

            //DoOutput设置是否向httpUrlConnection输出，DoInput设置是否从httpUrlConnection读入，此外发送post请求必须设置这两个
            conn.setDoOutput(true);
            conn.setDoInput(true);

            /**
             * 下面的三句代码，就是调用第三方http接口
             */
            //获取URLConnection对象对应的输出流
            out = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
            //发送请求参数即数据
            out.write(data);
            //flush输出流的缓冲
            out.flush();

            /**
             * 下面的代码相当于，获取调用第三方http接口后返回的结果
             */
            //获取URLConnection对象对应的输入流
            InputStream is = conn.getInputStream();
            //构造一个字符流缓存
//            br = new BufferedReader(new InputStreamReader(is));
            br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String str = "";
            while ((str = br.readLine()) != null) {
                result += str;
            }
            //将数据分割
            String[] resultArr = result.split("id:");
            List<String> resultList = new ArrayList<>();
            for (String aaa : resultArr) {
                if (aaa.contains("data:")) {
                    String[] resultData1 = aaa.split("data:");
                    resultList.add(resultData1[0]);
                    resultList.add(resultData1[1]);
                }
            }

            if (resultList.size() >= 4) {
                //java字符串转JSON对象
                com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(resultList.get(1));
                //可以直接从json对象里拿到想要的字段
                String content = jsonObject.getString("content");
                com.alibaba.fastjson.JSONObject contentObject = JSON.parseObject(content);
                String output = contentObject.getString("output");
                String contentType = jsonObject.getString("content_type");
                //java字符串转JSON对象
                com.alibaba.fastjson.JSONObject jsonUrlObject = JSON.parseObject(resultList.get(3));
                //可以直接从json对象里拿到想要的字段
                String debugUrl = jsonUrlObject.getString("debug_url");
                ResponseResult responseResult = new ResponseResult();
                responseResult.setContent(output);
                responseResult.setContentType(contentType);
                responseResult.setDebugUrl(debugUrl);
//                jsonInputString = JSON.toJSONString(responseResult);

                jsonInputString = output;
//                messageService.sendMessage(jsonInputString);
//                return jsonInputString;
            }
            //关闭流
            is.close();
            //断开连接，disconnect是在底层tcp socket链接空闲时才切断，如果正在被其他线程使用就不切断。
            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return jsonInputString;
    }
}
