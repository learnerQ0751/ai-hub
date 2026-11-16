-- V12：成本与用量统计（REQ-4.3）
-- 采用实时视图聚合 call_logs：口径与逐条日志一致，不引入重复聚合/定时任务漂移。
-- 日期按数据库会话时区截断（生产库统一 UTC）。

CREATE OR REPLACE VIEW v_usage_daily AS
SELECT CAST(created_at AS DATE) AS usage_date,
       tenant_id,
       project_id,
       model_alias,
       key_id,
       COUNT(1) AS requests,
       COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
       COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
       COALESCE(SUM(total_tokens), 0) AS total_tokens,
       COALESCE(SUM(cost), 0) AS cost
FROM call_logs
GROUP BY CAST(created_at AS DATE), tenant_id, project_id, model_alias, key_id;

COMMENT ON VIEW v_usage_daily IS '按天/租户/项目/模型/Key 的调用量、Token 与成本聚合视图';
