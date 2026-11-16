-- H2（PostgreSQL 兼容模式）测试 schema：与 Flyway V12 对齐。

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
