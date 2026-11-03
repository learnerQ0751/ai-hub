package com.qtua.aihub.common.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * URL 敏感信息掩码工具。
 *
 * <p>用于对外返回 MCP endpoint：掩码 userinfo（如 {@code user:pass@}）与常见敏感 query
 * 参数值（api_key/token/secret/password 等），避免密钥通过列表/详情接口泄露。</p>
 */
public final class UrlMasker {

    private static final Pattern USER_INFO = Pattern.compile("(//)([^/@\\s]+)@");
    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)([?&](?:api[_-]?key|apikey|key|token|secret|password|passwd)=)[^&\\s#]*");

    private UrlMasker() {
    }

    /**
     * 掩码 URL 中的敏感信息；null 或空串原样返回。
     *
     * @param url 原始 URL
     * @return 掩码后的 URL
     */
    public static String mask(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String masked = USER_INFO.matcher(url).replaceAll("$1***@");
        return SENSITIVE_QUERY.matcher(masked).replaceAll("$1***");
    }

    /**
     * 判断参数名是否为敏感名（预留：命令/args 中如需掩码可复用）。
     *
     * @param name 参数名
     * @return true 表示敏感
     */
    public static boolean isSensitiveName(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return lower.contains("apikey") || lower.contains("token")
                || lower.contains("secret") || lower.contains("password");
    }
}
