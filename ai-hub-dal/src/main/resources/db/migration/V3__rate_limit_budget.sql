-- V3：限流与预算（REQ-1.4）
-- budget_duration：预算周期（daily/monthly）；为空表示不启用周期预算。
-- error_type：调用失败/限流命中的错误类型，供统计与排障。
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS budget_duration VARCHAR(16);
ALTER TABLE call_logs ADD COLUMN IF NOT EXISTS error_type VARCHAR(64);

COMMENT ON COLUMN api_keys.budget_duration IS '预算周期：daily/monthly；为空表示不启用周期预算';
COMMENT ON COLUMN call_logs.error_type IS '错误/限流类型，如 rate_limit_error、budget_exceeded';
