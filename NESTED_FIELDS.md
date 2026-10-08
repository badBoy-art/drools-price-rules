# 页面选择多层属性并匹配原始JSON

## 页面配置

页面选择 `客户 → 地址 → 省份` 时，选中的属性标识依次为 `customer`、`address`、`province`。完整字段路径为 `customer.address.province`，同时写入入参定义的 `fields.name` 与条件的 `when.field`：

```json
{
  "fields": [
    {
      "name": "customer.address.province",
      "type": "STRING",
      "required": true,
      "description": "客户地址中的省份",
      "example": "浙江"
    }
  ],
  "rules": [
    {
      "id": "province_check",
      "kind": "BASE",
      "when": {
        "field": "customer.address.province",
        "operator": "EQ",
        "value": "浙江"
      },
      "actions": [{"type": "AUTO_APPROVE"}]
    }
  ]
}
```

以上为配置片段；完整配置还需 `ruleName` 与 `businessType`。页面的级联选择结果可这样转成路径：

```javascript
const selectedKeys = ["customer", "address", "province"];
const fieldPath = selectedKeys.join(".");
// fields[].name = fieldPath
// when.field = fieldPath
```

`when.value` 是页面输入或选择的比较常量，例如浙江；业务方请求中的省份值则从嵌套单据中读取。参数类型声明的是选中叶子值的类型，省份是 `STRING`；不需要额外声明 `customer`、`customer.address` 等父节点。

## 业务方请求

业务方调用 `POST /api/rules/execute`，保留JSON的对象层级：

```json
{
  "rule_name": "nested_order_approval",
  "document": {
    "id": "ORDER-NESTED-001",
    "data": {
      "orderAmount": 15000,
      "customer": {
        "id": "C001",
        "address": {"province": "浙江"}
      }
    }
  }
}
```

引擎以 `document.data` 为路径起点，读取 `customer.address.province` 得到字符串浙江，再进行比较。实际请求不应把它改成 `data["customer.address.province"]`；带点号的顶层字面键不会覆盖嵌套路径的值。

## 路径与校验规则

| 路径 | 对应原始JSON |
|---|---|
| `orderAmount` | `{"orderAmount":15000}` |
| `customer.address.province` | `{"customer":{"address":{"province":"浙江"}}}` |
| `items[0].price` | `{"items":[{"price":100}]}` |
| `orders[1].items[0].price` | 第二张订单的第一个明细价格 |
| `matrix[0][1]` | 二维数组第一行、第二列 |

- 路径最多512字符、20步；每个对象属性名最多64字符，必须字母或下划线开头，后续可含数字。
- 数组下标从0开始，为确定的非负整数；不支持通配符、过滤表达式、方法调用或脚本。
- 读取中遇到缺失节点、null或数组越界视为路径缺失。`required=true` 返回HTTP400；可选字段可使用 `EXISTS/NOT_EXISTS` 判断。
- 路径节点已存在，但应为对象/数组的节点类型错误，返回HTTP400；叶子值不符合声明类型也返回HTTP400。
- 缺失值的普通比较为false，包括 `NE/NOT_IN`。`NOT` 仍对布尔结果取反。
- 叶子可声明NUMBER、STRING、BOOLEAN、STRING_LIST；声明JSON的整个对象/数组只支持存在性判断。子属性可另以完整路径声明为标量并比较。
- 路径相对于data，不包含 `document.data.` 前缀；名称及字符串比较区分大小写。
- 业务出参 `outputs.name` / `SET_RESULT.key` 继续使用最多64字符的顶层结果键；本次路径支持用于业务入参。

## 共用参数与MySQL保存

多层路径继续使用共用 `ParameterDefinition`，来源规则的 `parameterSources.inputs` 可直接选择 `customer.address.province` 等完整路径。发布时在 `rule_version.config_json` 保存路径、叶子类型、必填约束、说明、示例及确定来源版本；`when.field` 和生成的DRL也保存同一路径。

执行时解析原始JSON，校验后在内部生成路径到叶子值的映射供DRL读取。应用INFO执行日志的 `input` 保留原始嵌套请求，不写执行日志表。历史版本按自身快照执行，字段路径不要求修改数据库结构。

## 可执行示例

完整配置：[nested-order-config.json](examples/nested-order-config.json)，请求：[execute-nested-order.json](examples/execute-nested-order.json)，实际DRL：[nested-order.drl](examples/nested-order.drl)，结果：[result-nested-order.json](examples/result-nested-order.json)。

```bash
curl -X POST http://localhost:8080/api/rules/nested_order_approval/publish \
  -H 'Content-Type: application/json' --data-binary @examples/nested-order-config.json

curl -X POST http://localhost:8080/api/rules/execute \
  -H 'Content-Type: application/json' --data-binary @examples/execute-nested-order.json
```

浙江省份返回 `MANUAL_APPROVAL`，业务结果 `reviewRegion=LOCAL`；其他省份返回 `AUTO_APPROVE` 和 `reviewRegion=REMOTE`。此示例用于展示路径匹配，实际审批政策由页面配置决定。
