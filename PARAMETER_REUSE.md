# 入参、出参共用与规则版本保存

## 共用模型

接口的规则选择参数、单据调用信息、版本信息、规则计算结果、价格百分数和HTTP错误分别由 `ApiModels` 的共用类定义。商品和订单的规则执行结果使用同一 `ExecutionResult`。原有HTTP字段名称及JSON层级保留，所有属性在对应共用类中有中文注释。

页面配置的 `fields` 和 `outputs` 共用 `ParameterDefinition`：

```json
{
  "name": "discountRate",
  "type": "NUMBER",
  "required": false,
  "description": "会员折扣系数，0.95表示95折",
  "example": 0.95
}
```

`fields` 定义业务入参，name支持customer.address.province、items[0].price等路径，数据来自原始嵌套 `document.data` 或价格适配层；`outputs` 定义业务出参，数据来自 `SET_RESULT`，业务方通过响应 `results` 读取。用途说明和示例只参与文档展示；示例不会作为默认业务值。

`outputs` 省略时继续支持旧的自由结果；显式传 `[]` 时不允许输出任何业务结果键。声明出参后，发布阶段检查动作的键、值类型与必填约束，执行阶段检查最终出参。必填出参在 `AUTO_APPROVE`、`MANUAL_APPROVAL`、`MATCHED` 中必须存在且非null；`NO_MATCH` 和 `REJECTED` 允许不返回。运行时契约失配返回HTTP500。

## 跨规则复用

来源规则先发布完整参数定义。新的商品或订单规则通过 `parameterSources` 选择需要复用的参数：

```json
{
  "ruleName": "order_shared_approval",
  "businessType": "ORDER",
  "parameterSources": [
    {
      "ruleName": "order_approval",
      "version": 1,
      "inputs": ["orderAmount", "customerLevel", "overdue", "tags", "region"],
      "outputs": ["discountRate"]
    }
  ],
  "rules": [
    {
      "id": "vip_discount",
      "kind": "BASE",
      "when": {"field": "customerLevel", "operator": "EQ", "value": "VIP"},
      "actions": [{"type": "SET_RESULT", "key": "discountRate", "value": 0.95}]
    }
  ]
}
```

来源 `version` 可省略，发布时选择该来源的最新版本并保存实际版本。引用只复制参数定义；当前规则的条件和动作由当前配置定义。来源入参从来源 `fields` 中选择，来源出参从来源 `outputs` 中选择。允许混合来源参数和本地定义；同名定义必须完全一致，包括用途说明及示例，否则发布失败。相同定义只保留一份，已展开配置再次发布不会重复添加参数。

完整可执行示例：[来源配置](examples/order-config.json)、[复用配置](examples/order-shared-config.json)、[业务请求](examples/execute-shared-order.json)、[发布后完整快照](examples/order-shared-snapshot.json)。

```bash
curl -X POST http://localhost:8080/api/rules/order_approval/publish \
  -H 'Content-Type: application/json' --data-binary @examples/order-config.json

curl -X POST http://localhost:8080/api/rules/order_shared_approval/publish \
  -H 'Content-Type: application/json' --data-binary @examples/order-shared-config.json

curl -X POST http://localhost:8080/api/rules/execute \
  -H 'Content-Type: application/json' --data-binary @examples/execute-shared-order.json
```

## MySQL rule_version 保存方式

数据库仅保留 `rule_version` 表，参数定义存入已有 `LONGTEXT config_json`。数据库中的一个版本是一份完整快照：

| 存储位置 | 内容 |
|---|---|
| `rule_version.rule_name` + `version` | 规则集与版本，组成主键 |
| `rule_version.config_json.fields` | 完整入参定义，包括来源复制的参数及本地参数 |
| `rule_version.config_json.outputs` | 完整业务出参定义，保留类型、必填约束、说明、示例 |
| `rule_version.config_json.parameterSources` | 来源规则名、确定来源版本、选中的入出参名 |
| `rule_version.config_json.rules` | 当前规则的条件与动作 |
| `rule_version.drl_content` | 根据同一份展开后配置生成并验证的DRL |
| 应用INFO执行日志的 `input` / `result` | 本次调用的实际入参值和出参值，不写MySQL |

表中的 `config_json` 是一个字符串列；上表的点号表示JSON内的属性。`creator_id`、`created_date`、`updater_id`、`updated_date` 保持已有定义。已有表无需增加列，新增配置直接写入现有列。

发布流程：读取来源版本 → 复制并合并参数 → 检查完整配置和出参常量 → 生成并编译DRL → 同一数据库事务保存配置与DRL的新版本。失败不会切换生效版本。新版本的引用不会自动跟随来源更新；需要复用来源的新参数定义时，修改来源版本或省略版本重新发布目标规则。

执行流程只读取目标规则的指定版本快照，不查询来源规则。这样来源定义后来更新，也不会改变已发布规则的字段类型、必填约束或历史结果。查询到的 `configJson` 已包含完整定义，页面可以直接编辑并重新发布。

读取指定版本：

```sql
SELECT rule_name, version, config_json, drl_content
FROM rule_version
WHERE rule_name = 'order_shared_approval' AND version = 1;
```

独立使用生成器时应传入展开后的完整配置；带来源引用的配置先通过 `ParameterResolver.resolve` 解析。HTTP发布接口已经封装上述流程。

多层页面选择及原始JSON请求见 [NESTED_FIELDS.md](NESTED_FIELDS.md)。所有HTTP入参与出参说明见 [API_PARAMETERS.md](API_PARAMETERS.md)。验证使用JDK 1.8.0_351、实际Drools编译/执行和H2的MySQL兼容模式；未连接实际MySQL执行建表或修改业务数据。
