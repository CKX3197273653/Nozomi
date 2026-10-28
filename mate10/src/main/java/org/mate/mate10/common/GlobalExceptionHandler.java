package org.mate.mate10.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.error.detail:false}")
    private boolean errorDetail;

    @ExceptionHandler(RegisterRejectedException.class)
    public Result<?> handleRegisterRejected(RegisterRejectedException e) {
        log.warn("注册被拒绝: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    //参数校验失败
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(400, "参数校验失败: " + msg);
    }
    //缺少必填参数
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return Result.error(400,"缺少必填参数: " + e.getParameterName());
    }
    //缺少上传的文件 / 表单部分
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<?> handleMissingPart(
            MissingServletRequestPartException e) {
        log.warn("缺少上传内容: {}", e.getRequestPartName());
        return Result.error(ResultCode.BAD_REQUEST, "缺少上传内容: " + e.getRequestPartName());
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: {}={}", e.getName(), e.getValue());
        return Result.error(400, "参数类型错误: " + e.getName());
    }

    //请求体格式错误
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return Result.error(400,"请求体格式错误，请检查 JSON 格式");
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

    //唯一约束,键取自MySQL 报错里的 for key
    private static final Map<String, String> UNIQUE_HINTS = Map.of(
            "qc_report.uk_report_no",  "报告编号已存在",
            "sys_user.uk_username",    "用户名已存在",
            "sys_user.uk_email",       "邮箱已存在",
            "sys_user.uk_user_emp_no", "该工号已被其他账号绑定",
            "sys_employee.uk_emp_no",  "该工号已在名册中"
    );

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKey(DuplicateKeyException e) {
        String cause = String.valueOf(e.getMostSpecificCause().getMessage());
        //Duplicate entry 'x' for key 'qc_report.uk_report_no' -> 取出约束名
        String hint = UNIQUE_HINTS.entrySet().stream()
                .filter(en -> cause.contains(en.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("数据已存在");

        log.warn("[Conflict] {} | {}", hint, cause);
        return Result.error(ResultCode.CONFLICT,
                errorDetail ? hint + "（" + cause + "）" : hint);
    }

    //未预期报错
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        //异常作为最后一个参数 → 打印完整堆栈
        log.error("未预期的异常", e);

        if (errorDetail) {
            // 开发环境-把异常类型和消息返回，方便定位
            return Result.error(ResultCode.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        // 生产环境-不暴露内部细节
        return Result.error(ResultCode.ERROR, "系统异常，请联系管理员");
    }
    // 上传文件超过大小限制
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, "上传文件过大，请压缩后重试");
    }
}
