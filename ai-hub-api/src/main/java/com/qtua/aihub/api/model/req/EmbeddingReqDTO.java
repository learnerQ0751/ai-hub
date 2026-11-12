package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI 兼容 embedding 请求。
 *
 * <p>{@code input} 支持字符串或字符串数组；{@code dimensions} 可选，用于请求供应商输出
 * 指定维度；{@code encoding_format} 仅支持 float。</p>
 */
public class EmbeddingReqDTO {

    /** 模型别名。 */
    private String model;
    /** 输入：字符串或字符串数组。 */
    private Object input;
    /** 可选输出维度。 */
    private Integer dimensions;
    /** 编码格式；仅支持 float。 */
    @JsonProperty("encoding_format")
    private String encodingFormat;
    /** 调用方标识（透传，不参与鉴权）。 */
    private String user;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Object getInput() {
        return input;
    }

    public void setInput(Object input) {
        this.input = input;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public void setDimensions(Integer dimensions) {
        this.dimensions = dimensions;
    }

    public String getEncodingFormat() {
        return encodingFormat;
    }

    public void setEncodingFormat(String encodingFormat) {
        this.encodingFormat = encodingFormat;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }
}
