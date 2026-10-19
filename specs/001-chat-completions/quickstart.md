# Quickstart: Chat Completions Gateway

## 1. 准备本地配置

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 编辑 application-local.yml，填入 DeepSeek key
```

> `application-local.yml` 已被 `.gitignore` 排除，不会提交。

## 2. 启动

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"
export PATH="$JAVA_HOME/bin:$PATH"

mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897"
```

## 3. 调用

```bash
curl -s http://127.0.0.1:18080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"chat-default","messages":[{"role":"user","content":"Say hi in one word."}]}'
```

预期：HTTP 200，OpenAI ChatCompletion JSON，含 `usage`。

## 4. 错误场景

```bash
# 未知模型
curl -i http://127.0.0.1:18080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"no-such-model","messages":[{"role":"user","content":"hi"}]}'

# 本期拒绝流式
curl -i http://127.0.0.1:18080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"chat-default","stream":true,"messages":[{"role":"user","content":"hi"}]}'
```

## 5. 质量门禁

```bash
mvn -Pquality verify
```
