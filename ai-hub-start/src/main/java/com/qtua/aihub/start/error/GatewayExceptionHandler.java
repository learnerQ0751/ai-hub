package com.qtua.aihub.start.error;

import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.api.model.res.ErrorResponse;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 网关统一异常处理。
 *
 * <p>把业务异常、请求体解析异常和未知异常转换为 OpenAI 兼容错误体；
 * 不把上游密钥或内部堆栈返回给客户端。</p>
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /**
     * 处理网关业务异常。
     *
     * @param ex      业务异常
     * @param request HTTP 请求（回填治理上下文）
     * @return 对应状态码与错误体
     */
    @ExceptionHandler(ChatCompletionException.class)
    public ResponseEntity<ErrorResponse> handleChatCompletion(ChatCompletionException ex,
                                                              HttpServletRequest request) {
        LOG.warn("gateway request failed: type={}, code={}, message={}",
                ex.getType(), ex.getCode(), ex.getMessage());
        markFailure(request, ex.getStatus().value(), ex.getType());
        return ResponseEntity.status(ex.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ex.getMessage(), ex.getType(), ex.getParam(), ex.getCode()));
    }

    /**
     * 处理请求体不是合法 JSON 的情况。
     *
     * @param ex      解析异常
     * @param request HTTP 请求（回填治理上下文）
     * @return 400 规范错误体
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        LOG.warn("request body is not readable: {}", ex.getMessage());
        markFailure(request, HttpStatus.BAD_REQUEST.value(), "invalid_request_error");
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("请求体不是合法的 JSON", "invalid_request_error", null, "400"));
    }

    /**
     * 处理请求方法不支持（如对 GET 接口发 POST）。
     *
     * @param ex      方法不支持异常
     * @param request HTTP 请求（回填治理上下文）
     * @return 405 规范错误体
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        LOG.warn("request method not supported: {}", ex.getMethod());
        markFailure(request, HttpStatus.METHOD_NOT_ALLOWED.value(), "invalid_request_error");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("请求方法不支持", "invalid_request_error", null, "405"));
    }

    /**
     * 处理缺少必需请求参数。
     *
     * @param ex      参数缺失异常
     * @param request HTTP 请求（回填治理上下文）
     * @return 400 规范错误体
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String param = ex.getParameterName();
        LOG.warn("missing request parameter: {}", param);
        markFailure(request, HttpStatus.BAD_REQUEST.value(), "invalid_request_error");
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("缺少必需的请求参数 '" + param + "'",
                        "invalid_request_error", param, "400"));
    }

    /**
     * 处理请求参数类型不匹配。
     *
     * @param ex      类型不匹配异常
     * @param request HTTP 请求（回填治理上下文）
     * @return 400 规范错误体
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String param = ex.getName();
        LOG.warn("request parameter type mismatch: {}", param);
        markFailure(request, HttpStatus.BAD_REQUEST.value(), "invalid_request_error");
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("请求参数 '" + param + "' 类型不正确",
                        "invalid_request_error", param, "400"));
    }

    /**
     * 处理上传文件超过 Spring multipart 上限。
     *
     * @param ex      上传大小异常
     * @param request HTTP 请求（回填治理上下文）
     * @return 400 规范错误体
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest request) {
        LOG.warn("upload size exceeded: {}", ex.getMessage());
        markFailure(request, HttpStatus.BAD_REQUEST.value(), "invalid_request_error");
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("上传文件大小超出服务端限制",
                        "invalid_request_error", "file", "400"));
    }

    /**
     * 兜底异常处理，避免泄露内部细节。
     *
     * @param ex      未知异常
     * @param request HTTP 请求（回填治理上下文）
     * @return 500 规范错误体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOG.error("unexpected gateway error", ex);
        markFailure(request, HttpStatus.INTERNAL_SERVER_ERROR.value(), "internal_error");
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("网关内部错误", "internal_error", null, "500"));
    }

    /**
     * 把失败状态回填到网关治理上下文，供 Filter 释放预占并记录日志。
     *
     * @param request   HTTP 请求
     * @param status    HTTP 状态码
     * @param errorType 错误类型
     */
    private void markFailure(HttpServletRequest request, int status, String errorType) {
        GatewayContext context = (GatewayContext) request.getAttribute(GatewayContext.ATTRIBUTE);
        if (!org.springframework.util.ObjectUtils.isEmpty(context)) {
            context.fail(status, errorType);
        }
    }
}
