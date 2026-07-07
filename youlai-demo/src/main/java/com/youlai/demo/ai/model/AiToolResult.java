package com.youlai.demo.ai.model;

public record AiToolResult<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    public static <T> AiToolResult<T> success(T data) {
        return new AiToolResult<>(true, "OK", "success", data);
    }

    public static <T> AiToolResult<T> fail(String code, String message) {
        return new AiToolResult<>(false, code, message, null);
    }
}
