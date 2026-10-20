package com.qtua.aihub.api.error;

import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
     * @param ex 业务异常
     * @return 对应状态码与错误体
     */
    @ExceptionHandler(ChatCompletionException.class)
    public ResponseEntity<ErrorResponse> handleChatCompletion(ChatCompletionException ex) {
        LOG.warn("gateway request failed: type={}, code={}, message={}",
                ex.getType(), ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ex.getMessage(), ex.getType(), ex.getParam(), ex.getCode()));
    }

    /**
     * 处理请求体不是合法 JSON 的情况。
     *
     * @param ex 解析异常
     * @return 400 规范错误体
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        LOG.warn("request body is not readable: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("Invalid JSON request body", "invalid_request_error", null, "400"));
    }

    /**
     * 兜底异常处理，避免泄露内部细节。
     *
     * @param ex 未知异常
     * @return 500 规范错误体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        LOG.error("unexpected gateway error", ex);
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("Internal gateway error", "internal_error", null, "500"));
    }
}
