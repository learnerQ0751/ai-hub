package com.qtua.aihub.gateway;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String message, String type, String param, String code) {
    }

    public static ErrorResponse of(String message, String type, String param, String code) {
        return new ErrorResponse(new ErrorBody(message, type, param, code));
    }
}
