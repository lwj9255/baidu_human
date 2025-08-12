package com.example.digital.human.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface VoiceTranslationService {
    //将语音转换为汉字
    public String getText(MultipartFile voiceFile) throws IOException;
}
