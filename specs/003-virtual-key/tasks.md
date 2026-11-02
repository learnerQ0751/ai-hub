# Tasks 003：虚拟 Key

- [x] T1 common：`KeyHasher` + 异常工厂
- [x] T2 dal：`api_keys` DDL、`ApiKeyDO`、`ApiKeyMapper`（MyBatis XML）
- [x] T3 manager：`ApiKeyBO`、`ApiKeyManager`（BO↔DO + JSON）
- [x] T4 api：`KeyService`、`KeyAuthDTO`、请求/响应 DTO
- [x] T5 service：`KeyServiceImpl`（generate/authenticate/info/block/unblock）
- [x] T6 core：白名单校验 + 未知模型后置判断
- [x] T7 start：`AuthFilter` 扩展、`KeyController`、Controller 传递认证上下文
- [x] T8 配置：datasource + SQL init；测试 H2 配置
- [x] T9 测试：common/dal/manager/service/core/start + 端到端 KeyManagementIntegrationTest
- [x] T10 `mvn -Pquality verify` 通过
- [x] T11 更新 REQ-1.3 记录、STATUS、PROJECT
