# QLExpress 价格规则配置

项目按 [schema.sql](src/main/resources/schema.sql) 使用 `ac_audit_rule` 保存规则编码和名称，使用 `ac_audit_rule_cond` 保存规则组、条件和对应的 QLExpress 脚本片段。API 的 `businessCode/businessName` 对应主表的 `rule_code/rule_name`。运营端保存时基于完整配置生成并校验片段后原子写入；execute 读取当前片段并运行，不再现场生成脚本。

使用 [schema.sql](src/main/resources/schema.sql) 创建 MySQL 表。数据库连接通过 `RULES_DB_URL`、`RULES_DB_USERNAME`、`RULES_DB_PASSWORD` 设置。已有数据库需自行迁移为当前两表结构并处理旧的 DRL/脚本表数据。

## HTTP API

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/api/rule-sets` | 新建业务规则及条件 |
| GET | `/api/rule-sets/{businessCode}` | 查询当前规则及条件 |
| PUT | `/api/rule-sets/{businessCode}` | 更新完整配置 |
| GET | `/api/rule-sets/{businessCode}/conditions` | 查询条件行 |
| POST | `/api/rule-sets/{businessCode}/conditions` | 新增条件行 |
| PUT | `/api/rule-sets/{businessCode}/conditions/{ruleCode}/{conditionCode}` | 更新条件行 |
| POST | `/api/rules/execute` | 执行当前配置 |

执行请求见 [execute.json](examples/execute.json)。请求中的 `params` 支持单个对象或对象数组。执行响应包含 `businessCode`、命中条件 `matches` 和应用赋值后的 `results`；数组输入时 `results[i]` 与 `matches[i]` 对应。脚本执行阶段只判断条件并记录命中，之后服务端读取命中项的结果分配信息，将多个 `field`、`value` 写入输入参数的副本，再放入 `results` 返回。路径支持 `a.b.c`。所有条件都基于原始入参判断；赋值按页面顺序应用，同一路径由最后应用的赋值覆盖。

页面的品类区间、风控配置和成本上调幅度规则都存为 `ac_audit_rule_cond` 行。规则组按 `rule_name` 聚合，一个组可有多条条件。审批动作、审批人及抄送对象不属于规则引擎配置。
