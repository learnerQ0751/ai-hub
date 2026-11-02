-- V4：调用日志容错轨迹（REQ-1.6）
-- retry_count：本请求内失败尝试次数；fallback_used：是否发生别名级 fallback；
-- served_alias：最终服务的别名（正常等于请求别名，fallback 时为备用别名）。
ALTER TABLE call_logs ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE call_logs ADD COLUMN IF NOT EXISTS fallback_used BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE call_logs ADD COLUMN IF NOT EXISTS served_alias VARCHAR(128);

COMMENT ON COLUMN call_logs.retry_count IS '本请求内失败尝试次数';
COMMENT ON COLUMN call_logs.fallback_used IS '是否发生别名级 fallback';
COMMENT ON COLUMN call_logs.served_alias IS '最终服务的别名；正常等于请求别名';
