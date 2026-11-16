package com.qtua.aihub.service.admin;

import com.qtua.aihub.api.UsageService;
import com.qtua.aihub.api.model.res.UsageItemResDTO;
import com.qtua.aihub.api.model.res.UsageResDTO;
import com.qtua.aihub.api.model.res.UsageTotalsResDTO;
import com.qtua.aihub.api.model.res.UsageTrendResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.pricing.PricingCatalog;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.common.tenant.TenantScope;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.manager.model.UsageAggregateBO;
import com.qtua.aihub.manager.usage.UsageManager;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本与用量查询实现（REQ-4.3）。
 *
 * <p>实时读取 `v_usage_daily`，与调用日志同口径；虚拟 Key 强制绑定租户与自己的 Key，
 * 不能通过参数横向查看其他租户。缺价模型单列返回。</p>
 */
@Service
public class UsageServiceImpl implements UsageService {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MASK_MIN_LENGTH = 8;
    private static final int MASK_VISIBLE = 4;
    private static final String PARAM_FROM = "from";
    private static final String PARAM_TO = "to";

    private final UsageManager usageManager;
    private final PricingCatalog pricingCatalog;
    private final TenantScopeGuard tenantScopeGuard;

    /**
     * 构造服务。
     *
     * @param usageManager     用量门面
     * @param pricingCatalog   价格目录
     * @param tenantScopeGuard 租户守卫
     */
    public UsageServiceImpl(UsageManager usageManager, PricingCatalog pricingCatalog,
                            TenantScopeGuard tenantScopeGuard) {
        this.usageManager = usageManager;
        this.pricingCatalog = pricingCatalog;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @Override
    public UsageResDTO query(String from, String to, String tenantId, String projectId,
                             String modelAlias, String keyId) {
        Query query = resolveQuery(from, to, tenantId, projectId, modelAlias, keyId);
        List<UsageAggregateBO> rows = usageManager.findUsage(query.from(), query.to(),
                query.tenantId(), query.projectId(), query.modelAlias(), query.keyId());
        return toResDTO(query, rows);
    }

    @Override
    public String exportCsv(String from, String to, String tenantId, String projectId,
                            String modelAlias, String keyId) {
        UsageResDTO result = query(from, to, tenantId, projectId, modelAlias, keyId);
        StringBuilder csv = new StringBuilder(
                "date,tenant_id,project_id,model_alias,key_id,requests,prompt_tokens,"
                        + "completion_tokens,total_tokens,cost\n");
        for (UsageItemResDTO item : result.items()) {
            csv.append(csvField(item.date())).append(',')
                    .append(csvField(item.tenantId())).append(',')
                    .append(csvField(item.projectId())).append(',')
                    .append(csvField(item.modelAlias())).append(',')
                    .append(csvField(item.keyId())).append(',')
                    .append(item.requests()).append(',')
                    .append(item.promptTokens()).append(',')
                    .append(item.completionTokens()).append(',')
                    .append(item.totalTokens()).append(',')
                    .append(item.cost().toPlainString()).append('\n');
        }
        return csv.toString();
    }

    private Query resolveQuery(String from, String to, String tenantId, String projectId,
                               String modelAlias, String keyId) {
        LocalDate end = parseDate(to, PARAM_TO, LocalDate.now());
        LocalDate start = parseDate(from, PARAM_FROM, end.minusDays(DEFAULT_RANGE_DAYS - 1L));
        if (start.isAfter(end)) {
            throw ChatCompletionException.invalidRequest("from 不能晚于 to", PARAM_FROM);
        }
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw ChatCompletionException.invalidRequest(
                    "查询区间不能超过 " + MAX_RANGE_DAYS + " 天", PARAM_FROM);
        }
        String effectiveTenant = StringUtils.hasText(tenantId) ? tenantId.trim() : null;
        String effectiveProject = StringUtils.hasText(projectId) ? projectId.trim() : null;
        String effectiveKey = StringUtils.hasText(keyId) ? keyId.trim() : null;
        TenantIdentity identity = tenantScopeGuard.currentIdentity();
        if (ObjectUtils.isEmpty(identity) || !identity.master()) {
            TenantScope scope = tenantScopeGuard.resolve(tenantId, projectId);
            effectiveTenant = scope.tenantId();
            effectiveProject = scope.projectId();
            if (!ObjectUtils.isEmpty(identity)) {
                effectiveKey = identity.keyHash();
            }
        }
        return new Query(start, end, effectiveTenant, effectiveProject,
                StringUtils.hasText(modelAlias) ? modelAlias.trim() : null, effectiveKey);
    }

    private LocalDate parseDate(String value, String param, LocalDate fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw ChatCompletionException.invalidRequest(
                    param + " 必须为 yyyy-MM-dd 格式", param);
        }
    }

    private UsageResDTO toResDTO(Query query, List<UsageAggregateBO> rows) {
        long requests = 0L;
        long promptTokens = 0L;
        long completionTokens = 0L;
        long totalTokens = 0L;
        BigDecimal cost = BigDecimal.ZERO;
        List<UsageItemResDTO> items = new ArrayList<>(rows.size());
        Map<LocalDate, UsageTrendResDTO> trend = new LinkedHashMap<>();
        for (UsageAggregateBO row : rows) {
            requests += row.requests();
            promptTokens += row.promptTokens();
            completionTokens += row.completionTokens();
            totalTokens += row.totalTokens();
            cost = cost.add(ObjectUtils.isEmpty(row.cost()) ? BigDecimal.ZERO : row.cost());
            items.add(new UsageItemResDTO(row.date().toString(), row.tenantId(),
                    row.projectId(), row.modelAlias(), maskKey(row.keyId()), row.requests(),
                    row.promptTokens(), row.completionTokens(), row.totalTokens(), row.cost()));
            UsageTrendResDTO previous = trend.get(row.date());
            trend.put(row.date(), new UsageTrendResDTO(row.date().toString(),
                    (ObjectUtils.isEmpty(previous) ? 0L : previous.requests()) + row.requests(),
                    (ObjectUtils.isEmpty(previous) ? 0L : previous.totalTokens()) + row.totalTokens(),
                    (ObjectUtils.isEmpty(previous) ? BigDecimal.ZERO : previous.cost()).add(row.cost())));
        }
        List<String> unpriced = unpricedModels(query);
        return new UsageResDTO(query.from().toString(), query.to().toString(),
                query.tenantId(), query.projectId(),
                new UsageTotalsResDTO(requests, promptTokens, completionTokens, totalTokens,
                        cost),
                items, List.copyOf(trend.values()), unpriced);
    }

    private List<String> unpricedModels(Query query) {
        List<String> used = usageManager.findUsedUpstreamModels(query.from(), query.to(),
                query.tenantId(), query.projectId());
        return used.stream().filter(model -> pricingCatalog.findPrice(model).isEmpty()).toList();
    }

    private String maskKey(String keyId) {
        if (!StringUtils.hasText(keyId)) {
            return null;
        }
        return keyId.length() <= MASK_MIN_LENGTH ? "****"
                : keyId.substring(0, MASK_VISIBLE) + "****"
                + keyId.substring(keyId.length() - MASK_VISIBLE);
    }

    private String csvField(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * 解析后的查询条件。
     */
    private record Query(LocalDate from, LocalDate to, String tenantId, String projectId,
                         String modelAlias, String keyId) {
    }
}
