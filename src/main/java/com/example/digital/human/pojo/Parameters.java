package com.example.digital.human.pojo;

//agent请求参数
public class Parameters {
    private String input;

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    @Override
    public String toString() {
        return "RequestParam{" +
                "input='" + input + '\'' +
                '}';
    }
}
