package com.youlai.decision.controller;

import com.youlai.common.result.Result;
import com.youlai.common.result.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * 全局异常处理器，将业务异常转换为前端易处理的 JSON 错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常。
     *
     * @param ex 参数校验异常
     * @return 错误响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        return Result.failed(ResultCode.PARAM_ERROR, "参数校验失败: " + ex.getMessage());
    }

    /**
     * 处理未找到资源异常。
     *
     * @param ex 未找到异常
     * @return 错误响应
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoSuchElementException.class)
    public Result<Void> handleNotFound(NoSuchElementException ex) {
        return Result.failed(ResultCode.RESOURCE_NOT_FOUND, ex.getMessage());
    }

    /**
     * 处理非法参数和非法状态异常。
     *
     * @param ex 业务异常
     * @return 错误响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Result<Void> handleBadRequest(RuntimeException ex) {
        return Result.failed(ResultCode.PARAM_ERROR, ex.getMessage());
    }
}
