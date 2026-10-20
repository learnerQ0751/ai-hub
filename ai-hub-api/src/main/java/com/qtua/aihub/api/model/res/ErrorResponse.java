package com.qtua.aihub.api.model.res;

/**
 * OpenAI 兼容错误体。
 *
 * <p>结构：{@code {"error":{"message","type","param","code"}}}。</p>
 *
 * @param error 错误详情
 */
public record ErrorResponse(ErrorBody error) {

    /**
     * 错误详情。
     *
     * @param message 错误描述
     * @param type    错误类型
     * @param param   出错字段
     * @param code    错误码
     */
    public record ErrorBody(String message, String type, String param, String code) {
    }

    /**
     * 构造错误体。
     *
     * @param message 错误描述
     * @param type    错误类型
     * @param param   出错字段
     * @param code    错误码
     * @return 错误响应
     */
    public static ErrorResponse of(String message, String type, String param, String code) {
        return new ErrorResponse(new ErrorBody(message, type, param, code));
    }
}
