package org.mate.mate10.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.error.detail:false}")
    private boolean errorDetail;

    //参数校验失败
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error("参数校验失败: " + msg);
    }
    //缺少必填参数
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return Result.error("缺少必填参数: " + e.getParameterName());
    }

    //请求体格式错误
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return Result.error("请求体格式错误，请检查 JSON 格式");
    }
    //接口不存在
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNotFound(NoResourceFoundException e) {
        log.warn("接口不存在: {}", e.getResourcePath());
        return Result.error(404, "接口不存在: " + e.getResourcePath());
    }
    //权限问题
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足，访问被拒绝");
        return Result.error(403, "没有权限");
    }

    //未预期报错
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        //异常作为最后一个参数 → 打印完整堆栈
        log.error("未预期的异常", e);

        if (errorDetail) {
            // 开发环境-把异常类型和消息返回，方便定位
            return Result.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        // 生产环境-不暴露内部细节
        return Result.error("系统异常，请联系管理员");
    }
}
