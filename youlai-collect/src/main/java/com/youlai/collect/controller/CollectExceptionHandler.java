package com.youlai.collect.controller;

import com.youlai.common.result.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CollectExceptionHandler {

    /**
     * 处理采集模块中的业务断言异常，并返回统一失败响应。
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Result<Void> handleBusinessException(RuntimeException ex) {
        return Result.failed(ex.getMessage());
    }

    /**
     * 处理请求参数校验异常，优先返回第一个字段校验错误。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数错误"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.failed(message);
    }
}
