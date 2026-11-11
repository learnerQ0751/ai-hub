# -*- coding: utf-8 -*-
"""REQ-3.6 / 三期毕业验收：MCP 真实链路演示脚本。

用法：
  python phase4-mcp-demo.py basic          # 默认跨项目关闭：RAG + 记忆 + 未授权
  python phase4-mcp-demo.py cross          # 跨项目开关开启：项目 B 读取项目 A 用户级记忆
  python phase4-mcp-demo.py model-switch   # embedding 模型切换未重建

依赖：Python 3 标准库；服务地址默认 http://localhost:18081。
输出：所有请求的 JSON-RPC 响应摘要，用于验收文档留证（不打印记忆正文以外的敏感信息）。
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:18081"
TOKEN = "sk-local-master-key"
TENANT = "t-demo"
USER = "u-demo"
PROJECT_A = "p-a"
PROJECT_B = "p-b"
PROJECT_RAG = "p-rag-demo"

DOCUMENTS = [
    ("demo-hr.md", "员工年假制度：入职满一年享受 5 天年假，满三年享受 10 天年假，满十年享受 15 天年假。"),
    ("demo-fin.md", "差旅住宿标准：一线城市每晚住宿不超过 600 元，二线城市不超过 400 元，其他城市不超过 300 元。"),
    ("demo-att.md", "考勤制度：每日 9:00 上班，18:00 下班，迟到超过 30 分钟按半天事假处理。"),
]


def http_post(path, payload, auth=True):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(BASE + path, data=body, method="POST")
    request.add_header("Content-Type", "application/json")
    if auth:
        request.add_header("Authorization", "Bearer " + TOKEN)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return response.status, dict(response.headers), response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        return exc.code, dict(exc.headers), exc.read().decode("utf-8")


_sequence = [0]


def rpc(method, params=None, auth=True):
    _sequence[0] += 1
    payload = {"jsonrpc": "2.0", "id": _sequence[0], "method": method}
    if params is not None:
        payload["params"] = params
    status, headers, text = http_post("/mcp", payload, auth=auth)
    return status, headers.get("X-Request-Id"), json.loads(text) if text else {}


def call_tool(name, arguments):
    status, request_id, body = rpc("tools/call", {"name": name, "arguments": arguments})
    if "error" in body:
        print("TOOL %-18s requestId=%s error=%s" % (name, request_id, body["error"]["message"]))
        return None, request_id
    content = body["result"]["content"][0]["text"]
    print("TOOL %-18s requestId=%s ok" % (name, request_id))
    return json.loads(content), request_id


def rest_post(path, payload):
    status, headers, text = http_post(path, payload)
    return status, json.loads(text) if text else {}


def print_json(label, value):
    print("--- %s ---" % label)
    print(json.dumps(value, ensure_ascii=False, indent=2))


def demo_tools():
    status, request_id, body = rpc("tools/list")
    names = [tool["name"] for tool in body["result"]["tools"]]
    print("tools/list status=%s requestId=%s tools=%s" % (status, request_id, ",".join(names)))
    status401, _, text401 = http_post("/mcp", {"jsonrpc": "2.0", "id": 999, "method": "tools/list"},
                                      auth=False)
    print("unauthorized tools/list status=%s body=%s" % (status401, text401))


def demo_rag():
    for file_name, content in DOCUMENTS:
        document, _ = call_tool("ingest_document", {
            "project_id": PROJECT_RAG, "file_name": file_name, "content": content})
        if document:
            print("  ingested doc_id=%s source=%s status=%s chunks=%s"
                  % (document["doc_id"], document["source"], document["index_status"],
                     document["chunk_count"]))

    hit, _ = call_tool("search_knowledge", {
        "query": "年假有多少天", "project_id": PROJECT_RAG, "top_k": 3,
        "min_score": 0.2, "rerank": True})
    if hit:
        print_json("search_knowledge 年假", {
            "count": hit["count"], "no_hit": hit["no_hit"], "reranked": hit["reranked"],
            "top1": hit["results"][0]["source"] if hit["results"] else None,
            "score": round(hit["results"][0]["score"], 4) if hit["results"] else None,
        })

    answer, _ = call_tool("rag_answer", {
        "query": "年假有多少天", "project_id": PROJECT_RAG, "model": "chat-default", "top_k": 4})
    if answer:
        print_json("rag_answer 年假", {
            "answer": answer["answer"],
            "citations": [{"index": c["index"], "source": c["source"],
                           "doc_id": c["doc_id"], "score": round(c["score"], 4)}
                          for c in answer["citations"]],
            "usage": answer["usage"],
        })

    no_hit, _ = call_tool("search_knowledge", {
        "query": "火星基地的氧气供应方案", "project_id": PROJECT_RAG,
        "top_k": 3, "min_score": 0.6})
    if no_hit:
        print_json("search_knowledge 无召回", {"count": no_hit["count"], "no_hit": no_hit["no_hit"]})

    fallback, _ = call_tool("rag_answer", {
        "query": "火星基地的氧气供应方案", "project_id": PROJECT_RAG, "model": "chat-default",
        "min_score": 0.6})
    if fallback:
        print_json("rag_answer 无召回", {
            "answer": fallback["answer"], "no_hit": fallback["no_hit"],
            "citations": fallback["citations"], "usage": fallback["usage"]})


def demo_memory_isolated():
    saved, _ = call_tool("save_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_A, "user_id": USER,
        "content": "用户对芒果过敏", "metadata": {"source": "chat"}, "ttl_seconds": 3600})
    if saved:
        print("  saved memory_id=%s type=%s expires_at=%s"
              % (saved["memory_id"], saved["memory_type"], saved["expires_at"]))

    recalled, _ = call_tool("recall_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_A, "user_id": USER, "keyword": "芒果"})
    if recalled:
        print_json("recall_memory 项目 A", {
            "count": recalled["count"],
            "contents": [m["content"] for m in recalled["memories"]]})

    other_project, _ = call_tool("recall_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_B, "user_id": USER})
    if other_project:
        print_json("recall_memory 项目 B（默认隔离）", {"count": other_project["count"]})

    rejected, request_id = call_tool("recall_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_B, "user_id": USER, "cross_project": True})
    print("  cross_project rejected requestId=%s" % request_id)

    call_tool("save_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_A, "user_id": USER,
        "session_id": "s-demo", "content": "会话内用户提到预算 5000"})
    session_rejected, _ = call_tool("recall_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_B, "user_id": USER,
        "session_id": "s-demo", "cross_project": True})
    other_tenant, _ = call_tool("recall_memory", {
        "tenant_id": "t-other", "project_id": PROJECT_A, "user_id": USER})
    if other_tenant:
        print_json("recall_memory 跨租户", {"count": other_tenant["count"]})

    cleared, _ = call_tool("clear_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_A, "user_id": USER,
        "reason": "用户行使删除权"})
    if cleared:
        print("  cleared deleted=%s audit_id=%s" % (cleared["deleted"], cleared["audit_id"]))
    after, _ = call_tool("recall_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_A, "user_id": USER})
    if after:
        print_json("recall_memory clear 之后", {"count": after["count"]})


def demo_memory_cross():
    saved, _ = call_tool("save_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_A, "user_id": USER,
        "content": "用户画像：偏好中文回答"})
    if saved:
        print("  saved memory_id=%s type=%s" % (saved["memory_id"], saved["memory_type"]))

    isolated, _ = call_tool("recall_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_B, "user_id": USER})
    if isolated:
        print_json("项目 B 默认召回（不开 cross_project）", {"count": isolated["count"]})

    shared, _ = call_tool("recall_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_B, "user_id": USER, "cross_project": True})
    if shared:
        print_json("项目 B 授权跨项目召回", {
            "count": shared["count"],
            "memories": [{"project_id": m["project_id"], "content": m["content"]}
                         for m in shared["memories"]]})

    call_tool("save_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_A, "user_id": USER,
        "session_id": "s-cross", "content": "会话级内容"})
    call_tool("recall_memory", {
        "tenant_id": TENANT, "project_id": PROJECT_B, "user_id": USER,
        "session_id": "s-cross", "cross_project": True})


def demo_model_switch():
    import urllib.parse
    url = BASE + "/v1/rag/documents?" + urllib.parse.urlencode({"project_id": PROJECT_RAG})
    request = urllib.request.Request(url)
    request.add_header("Authorization", "Bearer " + TOKEN)
    with urllib.request.urlopen(request, timeout=30) as response:
        documents = json.loads(response.read().decode("utf-8"))
    print_json("文档列表（切换模型后）", {
        "documents": [{"source": d["source"], "index_status": d["index_status"],
                       "embedding_model": d["embedding_model"],
                       "reindex_required": d["reindex_required"]}
                      for d in documents["documents"]]})

    hit, _ = call_tool("search_knowledge", {
        "query": "年假有多少天", "project_id": PROJECT_RAG, "top_k": 3, "min_score": 0.2})
    if hit:
        print_json("切换模型未重建：检索", {"count": hit["count"], "no_hit": hit["no_hit"]})

    fallback, _ = call_tool("rag_answer", {
        "query": "年假有多少天", "project_id": PROJECT_RAG, "model": "chat-default"})
    if fallback:
        print_json("切换模型未重建：回答", {
            "answer": fallback["answer"], "no_hit": fallback["no_hit"],
            "citations": fallback["citations"], "usage": fallback["usage"]})

    target = [d for d in documents["documents"] if d["source"] == "demo-att.md"][0]
    status, body = rest_post("/v1/rag/documents/" + target["doc_id"]
                             + "/reindex?project_id=" + PROJECT_RAG, {})
    print("reindex doc_id=%s source=%s status=%s index_status=%s embedding_model=%s"
          % (target["doc_id"], target["source"], status, body.get("index_status"),
             body.get("embedding_model")))

    rebuilt, _ = call_tool("search_knowledge", {
        "query": "考勤制度：每日 9:00 上班，18:00 下班，迟到超过 30 分钟按半天事假处理。",
        "project_id": PROJECT_RAG, "top_k": 3, "min_score": 0.2})
    if rebuilt:
        print_json("重建后检索", {
            "count": rebuilt["count"], "no_hit": rebuilt["no_hit"],
            "top1": rebuilt["results"][0]["source"] if rebuilt["results"] else None})


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "basic"
    print("==== mode=%s ====" % mode)
    if mode == "basic":
        demo_tools()
        demo_rag()
        demo_memory_isolated()
    elif mode == "cross":
        demo_memory_cross()
    elif mode == "model-switch":
        demo_model_switch()
    else:
        raise SystemExit("unknown mode: " + mode)


if __name__ == "__main__":
    main()
