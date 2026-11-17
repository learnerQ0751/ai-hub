-- V15：虚拟 Key 别名（控制台 Key 列表展示用）
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS alias VARCHAR(128);

COMMENT ON COLUMN api_keys.alias IS '管理台展示用别名；为空表示未设置';
