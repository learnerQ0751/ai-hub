package com.qtua.aihub.common.tenant;

/**
 * 请求级租户上下文（ThreadLocal）。
 *
 * <p>由入口过滤器在鉴权成功后写入、请求结束时清理，避免线程池复用串租户。
 * 后台线程如需访问租户信息，必须显式复制上下文，不得依赖继承的 ThreadLocal。</p>
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantIdentity> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    /**
     * 写入当前租户身份。
     *
     * @param identity 身份
     */
    public static void set(TenantIdentity identity) {
        CURRENT.set(identity);
    }

    /**
     * 当前租户身份。
     *
     * @return 身份；未设置时为 null（单元测试/内部调用）
     */
    public static TenantIdentity current() {
        return CURRENT.get();
    }

    /**
     * 清理上下文，必须在 finally 中调用。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
