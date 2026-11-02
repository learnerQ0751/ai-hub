-- H2 测试兼容：生产使用 PostgreSQL JSONB；H2 用 VARCHAR 域承接，
-- 使 CAST(... AS JSONB) 直接保存 JSON 文本，避免 H2 把文本再包成 JSON 字符串。
CREATE DOMAIN IF NOT EXISTS JSONB AS VARCHAR;
