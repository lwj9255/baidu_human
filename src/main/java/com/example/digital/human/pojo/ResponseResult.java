package com.example.digital.human.pojo;

//agent回答返回值
public class ResponseResult {
    private String content;
    private String contentType;
    private String debugUrl;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getDebugUrl() {
        return debugUrl;
    }

    public void setDebugUrl(String debugUrl) {
        this.debugUrl = debugUrl;
    }

    @Override
    public String toString() {
        return "ResponseResult{" +
                "content='" + content + '\'' +
                ", contentType='" + contentType + '\'' +
                ", debugUrl='" + debugUrl + '\'' +
                '}';
    }
}
