package com.qtua.aihub.integration.prompt;

import com.qtua.aihub.common.exception.ChatCompletionException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单模板渲染器。
 *
 * <p>只识别 {@code {{var}}} 占位符（单次纯文本替换，替换值不再解析，防止递归注入）；
 * 提供变量提取、声明一致性校验与渲染。</p>
 */
@Component
public class SimpleTemplateRenderer {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    /**
     * 提取模板中的变量集合（保持出现顺序、去重）。
     *
     * @param content 模板内容
     * @return 变量名集合
     */
    public Set<String> extractVariables(String content) {
        Set<String> variables = new LinkedHashSet<>();
        if (!StringUtils.hasText(content)) {
            return variables;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    /**
     * 校验变量声明与模板占位符一致（缺失/多余都拒绝）。
     *
     * @param content   模板内容
     * @param variables 声明的变量
     */
    public void validateDeclaration(String content, Iterable<String> variables) {
        Set<String> declared = new HashSet<>();
        for (String variable : variables) {
            declared.add(variable);
        }
        Set<String> used = extractVariables(content);
        Set<String> missing = new HashSet<>(used);
        missing.removeAll(declared);
        Set<String> extra = new HashSet<>(declared);
        extra.removeAll(used);
        if (!missing.isEmpty()) {
            throw ChatCompletionException.invalidRequest(
                    "模板使用了未声明的变量：" + missing, "variables");
        }
        if (!extra.isEmpty()) {
            throw ChatCompletionException.invalidRequest(
                    "变量声明未被模板使用：" + extra, "variables");
        }
    }

    /**
     * 渲染模板（缺变量 400，param 指向变量名）。
     *
     * @param content   模板内容
     * @param variables 变量值
     * @return 渲染结果
     */
    public String render(String content, Map<String, String> variables) {
        Set<String> used = extractVariables(content);
        for (String name : used) {
            if (!variables.containsKey(name)) {
                throw ChatCompletionException.invalidRequest(
                        "缺少模板变量 '" + name + "' 的取值", name);
            }
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(variables.get(name)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
