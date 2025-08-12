package com.example.digital.human.pojo;

//agent请求参数
public class RequestResult {
    private String workflowId;
    private String appId;
    private Parameters parameters;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "AnswerResult{" +
                "workFlowId='" + workflowId + '\'' +
                ", appId='" + appId + '\'' +
                ", requestParam=" + parameters +
                '}';
    }
}
