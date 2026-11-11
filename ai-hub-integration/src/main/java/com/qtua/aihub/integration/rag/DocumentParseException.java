package com.qtua.aihub.integration.rag;

/**
 * 文档解析失败。
 *
 * <p>由集成层的 PDFBox / 文本解码抛出，Service 转换为 400 中文错误，
 * 同时保留原始文件并落 FAILED 文档。</p>
 */
public class DocumentParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造解析异常。
     *
     * @param message 中文错误描述
     */
    public DocumentParseException(String message) {
        super(message);
    }

    /**
     * 构造解析异常并保留 cause。
     *
     * @param message 中文错误描述
     * @param cause   原始异常
     */
    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
