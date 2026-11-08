package com.badminton.mes.common.exception;

import java.util.stream.Collectors;

import com.badminton.mes.common.core.CommonResult;
import com.badminton.mes.common.core.ErrorCode;
import com.badminton.mes.common.core.GlobalErrorCodeConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理器，把各类异常统一转换为 {@link CommonResult} 响应。
 *
 * <p>HTTP 状态码按错误码来源映射(API-003)：A 类用户端错误返回 400，
 * B 类系统错误返回 500，C 类第三方错误返回 502；
 * 登录失效 A0230 与权限不足 A0301 例外，分别映射 401/403。
 *
 * <p>日志级别按黄山版日志规约区分：用户输入类错误记 warn(LOG-012)，
 * 系统未知异常记 error 并带堆栈(LOG-009)；出错详情不回传数据库等内部信息。
 *
 * @author 张竹灏
 * @date 2026/07/07
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常：Service 层业务规则校验不通过。
     *
     * @param exception 业务异常
     * @return 携带业务错误码的响应
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<CommonResult<Void>> handleServiceException(ServiceException exception) {
        // 业务异常已经携带稳定错误码；日志记录内部定位信息，响应只返回前端约定的错误结构。
        ErrorCode errorCode = exception.getErrorCode();
        logger.warn("[业务异常] code: {}, message: {}", errorCode.code(), exception.getMessage());
        return buildResponse(errorCode, exception.getMessage());
    }

    /**
     * 处理请求体(@RequestBody)与查询对象(@ModelAttribute)的参数校验失败。
     *
     * @param exception 参数校验异常
     * @return A0400 参数错误响应，message 逐条列出字段错误便于排查
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResult<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                // 将字段名和校验消息拼成可读文本，便于前端日志定位具体输入项。
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        logger.warn("[参数校验失败] {}", detail);
        return buildResponse(GlobalErrorCodeConstants.PARAM_ERROR, detail);
    }

    /**
     * 处理方法级参数校验失败，例如 @PathVariable、@RequestParam 上的约束注解。
     *
     * @param exception 方法参数校验异常
     * @return A0400 参数错误响应
     */
    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<CommonResult<Void>> handleMethodValidationException(Exception exception) {
        logger.warn("[参数校验失败] {}", exception.getMessage());
        return buildResponse(GlobalErrorCodeConstants.PARAM_ERROR,
                GlobalErrorCodeConstants.PARAM_ERROR.message());
    }

    /**
     * 处理必填查询参数缺失。
     *
     * @param exception 缺少查询参数异常
     * @return A0400 参数错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResult<Void>> handleMissingRequestParameterException(
            MissingServletRequestParameterException exception) {
        String detail = exception.getParameterName() + ": 必填查询参数不能为空";
        logger.warn("[查询参数缺失] {}", detail);
        return buildResponse(GlobalErrorCodeConstants.PARAM_ERROR, detail);
    }

    /**
     * 处理 multipart 请求缺少文件部件。
     *
     * <p>不处理会落到兜底分支，用户少传一个文件就得到「系统繁忙」，与实际情况不符。
     *
     * @param exception 缺少请求部件异常
     * @return A0400 参数错误响应
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CommonResult<Void>> handleMissingServletRequestPartException(
            MissingServletRequestPartException exception) {
        String detail = exception.getRequestPartName() + ": 必填文件不能为空";
        logger.warn("[请求部件缺失] {}", detail);
        return buildResponse(GlobalErrorCodeConstants.PARAM_ERROR, detail);
    }

    /**
     * 处理上传文件超过容器上限。
     *
     * <p>容器上限（{@code spring.servlet.multipart.max-file-size}）比业务上限略高，
     * 落在两者之间时由业务层拒绝，超过容器上限才会走到这里；
     * 同样必须是参数错误而不是系统错误。
     *
     * @param exception 上传超限异常
     * @return A0400 参数错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResult<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        logger.warn("[上传文件超过容器上限] {}", exception.getMessage());
        return buildResponse(GlobalErrorCodeConstants.PARAM_ERROR,
                "上传文件超过大小上限");
    }

    /**
     * 处理请求体 JSON 解析失败。
     *
     * @param exception JSON 解析异常
     * @return A0427 解析失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResult<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        logger.warn("[请求体解析失败] {}", exception.getMessage());
        return buildResponse(GlobalErrorCodeConstants.JSON_PARSE_ERROR,
                GlobalErrorCodeConstants.JSON_PARSE_ERROR.message());
    }

    /**
     * 兜底处理未知异常。
     *
     * <p>记录 error 日志并带完整堆栈(LOG-009)；响应中只返回通用描述，
     * 不把内部异常细节泄露给前端。
     *
     * @param exception 未知异常
     * @return B0001 系统错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<Void>> handleException(Exception exception) {
        // 未知异常不把堆栈和数据库信息返回给用户，只在服务端保留完整堆栈供排查。
        logger.error("[系统未知异常] errorMessage: {}", exception.getMessage(), exception);
        return buildResponse(GlobalErrorCodeConstants.SYSTEM_ERROR,
                GlobalErrorCodeConstants.SYSTEM_ERROR.message());
    }

    /**
     * 按错误码来源映射 HTTP 状态码并组装响应体。
     *
     * <p>登录失效与权限不足先按语义化状态码特判(401/403)，
     * 供前端区分"引导重新登录"与"提示无权限"两类处理。
     *
     * @param errorCode 错误码对象
     * @param message   具体错误描述
     * @return 带 HTTP 状态码的统一响应
     */
    private ResponseEntity<CommonResult<Void>> buildResponse(ErrorCode errorCode, String message) {
        // 先声明状态变量，再根据错误码来源分级映射 HTTP 状态，避免 Controller 各自处理异常协议。
        HttpStatus status;
        if (GlobalErrorCodeConstants.UNAUTHORIZED.code().equals(errorCode.code())) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (GlobalErrorCodeConstants.FORBIDDEN.code().equals(errorCode.code())) {
            status = HttpStatus.FORBIDDEN;
        } else {
            status = switch (errorCode.code().charAt(0)) {
                // A 类：用户端错误
                case 'A' -> HttpStatus.BAD_REQUEST;
                // C 类：第三方服务出错
                case 'C' -> HttpStatus.BAD_GATEWAY;
                // B 类及其他：系统内部错误
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }
        // CommonResult 负责响应体，ResponseEntity 负责 HTTP 状态，两层协议同时满足前后端约定。
        return ResponseEntity.status(status).body(CommonResult.error(errorCode, message));
    }
}
