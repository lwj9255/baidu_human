package com.example.digital.human.controller;

import com.example.digital.human.rabbitmq.MessageService;
import com.example.digital.human.service.VoiceTranslationService;
import com.example.digital.human.websocket.handler.AiFlagWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    @Resource
    private VoiceTranslationService translationService;

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
     * 获取关闭智能体标记
     *
     * @param closeFlag
     * @return
     */
    @PostMapping("/close")
    public void getCloseFlag(String closeFlag) {
        AiFlagWebSocketHandler.sendMessageToAll(closeFlag);
    }
}
