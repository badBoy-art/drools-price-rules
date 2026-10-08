# HTTP 入参与出参说明

所有业务调用使用 JSON 请求体与 `Content-Type: application/json`。
金额字段按价格传值；百分数字段中 `5` 表示5%，`0.05` 表示0.05%。

标识格式：1到64个字母、数字、下划线、冒号或短横线。品类ID使用此格式。

## 通用规则计算：POST /api/rules/execute

### ExecutionRequest

| 属性 | 用途与约束 |
|---|---|
| `rule_name` | 规则集调用标识，JSON字段为 rule_name；必填，例如 sales_price、cost_price 或 order_approval，用于定位已发布的规则集；1到64个字母、数字、下划线、冒号或短横线。 |
| `version` | 指定执行的历史规则版本；可选，必须大于0；不传或传null时使用当前生效版本。 |
| `document` | 待执行规则的单据对象；必填，包含单据号和data业务字段；价格规则传入原始价格字段，计算方式从发布配置读取。 |

### Document

| 属性 | 用途与约束 |
|---|---|
| `id` | 业务单据号；必填、非空，最长128个字符，例如 SO-1001；用于将规则执行日志关联到业务单据。 |
| `categoryPath` | 商品品类ID路径；旧价格品类配置可选；通用规则可在data中传入声明的品类或其他业务字段。 |
| `grossMarginPct` | 兼容旧配置的毛利率字段。新配置使用calculation计算时不要传此字段，传salePrice和costPrice。 |
| `costIncreasePct` | 兼容旧配置的成本上调幅度字段。新配置使用calculation计算时不要传此字段，传originalCostPrice和newCostPrice。 |
| `data` | 通用业务输入JSON对象；键对应fields.name，按照声明检查类型与必填项。未声明键不参与计算。NUMBER传JSON数值，不能传字符串；允许嵌套对象/数组，fields.name可为customer.address.province或items[0].price，业务请求保持原始JSON层级。 |

服务端补入 `documentId=document.id`；旧价格配置根据 `categoryPath` 匹配品类策略。配置声明calculation时，服务端先按受控类型从data的baseField和compareField计算resultField，再执行全部基础区间和风控规则。resultField由引擎生成，业务请求不能覆盖。公式选项为GROSS_MARGIN=(base-compare)/base×100、COST_INCREASE=(compare-base)/base×100；分母必须大于0，另一价格不能小于0。支持相对于document.data的多层属性路径及明确数组下标。见[多层属性匹配](NESTED_FIELDS.md)。

## 页面配置发布：POST /api/rules/{name}/publish

路径参数 `name` 为规则集标识，例如 `sales_price`，必须与请求体 `ruleName` 一致。

### RuleConfig（通用商品、订单及其他单据）

| 属性 | 用途与约束 |
|---|---|
| `ruleName` | 必填规则集标识，与URL的name一致。执行接口通过rule_name引用。 |
| `businessType` | 必填自定义业务标识，例如ORDER、SALES_PRICE、COST_PRICE、PURCHASE_ORDER；符合标识格式。 |
| `fields` | 入参定义，发布后1到200项；可由parameterSources提供，本地定义和来源定义合并；不能有重名冲突。 |
| `rules` | 必填，1到20000条规则；规则ID不能重复。 |
| `outputs` | 可选业务出参定义，最多200项，与fields共用ParameterDefinition；省略兼容旧的自由结果，[]禁止任何SET_RESULT键。 |
| `parameterSources` | 可选，最多100个已发布规则参数来源；发布时复制选定入出参并保存确定来源版本。 |
| `calculation` | 可选受控计算选项。GROSS_MARGIN或COST_INCREASE；选择两个NUMBER输入字段和一个已声明的NUMBER结果字段。公式由服务端固定，不接受表达式代码。 |

### ParameterDefinition / Field（入参与出参共用）

| 属性 | 用途与约束 |
|---|---|
| `name` | 必填。入参是相对于document.data的路径，如customer.address.province、items[0].price，最多512字符/20步；每个属性名最多64字符，字母/下划线开头，后续可含数字。出参仍是results顶层键，最多64字符。 |
| `type` | 必填：NUMBER十进制数、STRING字符串、BOOLEAN布尔、STRING_LIST字符串数组、JSON任意JSON值。JSON入参只支持EXISTS/NOT_EXISTS判断。 |
| `required` | 默认false。入参true时缺失/null报400；出参true时在AUTO_APPROVE、MANUAL_APPROVAL、MATCHED结果中必须有非null值，失配返回500；NO_MATCH/REJECTED允许缺失。 |
| `description` | 可选参数用途说明，最多2000字符；随当前版本快照保存，供页面及文档展示。 |
| `example` | 可选同类型示例值；随版本保存，不是默认值，不会参与实际计算。 |

不同 `rule_name` 可以声明不同的 `outputs`，每个已发布版本都保存自己的出参契约。比如审批层级可声明 `{"name":"score","type":"NUMBER","required":true}`；规则命中后配置 `SET_RESULT` 设置 `score`，业务方从响应的 `results.score` 读取。页面可以按业务调整输出键和类型，不需要为不同业务增加专用HTTP接口。

### Calculation（受控计算）

| 属性 | 用途与约束 |
|---|---|
| `type` | 必填受控枚举：GROSS_MARGIN=(base-compare)/base×100；COST_INCREASE=(compare-base)/base×100。 |
| `baseField` | 必填输入字段路径，必须在fields中声明为NUMBER；同时作为计算分母，值须大于0。 |
| `compareField` | 必填输入字段路径，必须在fields中声明为NUMBER；值不能小于0。 |
| `resultField` | 必填结果字段名，须在fields中声明为NUMBER。由服务端计算，业务请求不能传入覆盖。 |

`GROSS_MARGIN`通常将salePrice作为baseField、costPrice作为compareField；`COST_INCREASE`将originalCostPrice作为baseField、newCostPrice作为compareField。计算结果保留12位小数供展示，规则边界比较使用未舍入比例。

### ParameterSource（跨规则复用参数）

| 属性 | 用途与约束 |
|---|---|
| `ruleName` | 必填来源规则集标识；定位来源rule_version记录，使用camelCase。 |
| `version` | 可选来源版本号，必须大于0；省略时在发布阶段读取最新版本，写入快照时替换成确定版本。 |
| `inputs` | 可选来源入参名数组，最多200项，不能重复；名称必须存在于来源fields。 |
| `outputs` | 可选来源出参名数组，最多200项，不能重复；名称必须存在于来源outputs。 |

每项来源至少选择一个入参或出参。同名本地/来源定义必须完全相同，包括类型、必填、说明、示例；一致时只保留一份，不一致报400。来源不存在报404。条件与动作由当前规则集自行配置；来源的规则动作不会被复制。来源定义必须已发布，调用端传入的只有实际业务值，参数类型说明不随每次业务请求重复提交。

### Rule

| 属性 | 用途与约束 |
|---|---|
| `id` | 必填规则ID，1到129个字母、数字、下划线、冒号或短横线；matchedRules使用ruleName:id。 |
| `name` | 可选展示名称，不参与条件和DRL代码。 |
| `kind` | 必填BASE或SUPPLEMENT。BASE同时命中时仅采用最高优先级一条；SUPPLEMENT全部采用。 |
| `priority` | 默认0；越大越优先，同优先级按rules配置顺序。允许负值。 |
| `when` | 必填条件树，最大10层、每条规则最多1000节点。 |
| `actions` | 必填动作数组，最多100项；[]仅记录命中。 |

### Condition

叶节点填写field/operator/value；组合节点填写logic/conditions；两种形式不能混用。

| 属性 | 用途与约束 |
|---|---|
| `field` | 叶节点必填，必须与fields.name中的完整路径一致，例如customer.address.province。 |
| `operator` | 叶节点必填，取值与字段类型对应，见下表。 |
| `value` | 叶节点比较常量，与字段类型一致；存在性运算省略；集合运算传数组。普通比较不接受null。 |
| `logic` | 组合节点必填：ALL对应AND，ANY对应OR，NOT对应取反。 |
| `conditions` | 组合节点必填、非空；NOT恰好一个子条件，ALL/ANY至少一个。 |

| operator | 支持字段类型 | value | 含义 |
|---|---|---|---|
| GT / GE / LT / LE | NUMBER | JSON数值 | 大于 / 大于等于 / 小于 / 小于等于 |
| EQ / NE | NUMBER / STRING / BOOLEAN | 同类型标量 | 等于 / 不等于，数值按数学值比较 |
| IN / NOT_IN | NUMBER / STRING / BOOLEAN | 1到1000个同类型标量数组 | 属于 / 不属于集合 |
| CONTAINS | STRING / STRING_LIST | 字符串 | 字符串包含子串，或字符串数组包含完整元素 |
| CONTAINS_ANY | STRING / STRING_LIST | 1到1000个字符串数组 | 包含任一子串或元素 |
| EXISTS / NOT_EXISTS | 全部 | 省略 | 字段有非null值 / 字段缺失或为null |

路径中节点缺失/null或数组越界视为字段缺失；必填字段报400，可选字段参与存在性判断；已有中间节点结构错误或叶子类型错误均报400。缺失/null字段的普通比较均为false，包括NE和NOT_IN。NOT对整个子条件的布尔结果取反，因此NOT(EQ)在缺失时为true；要求字段存在时另加EXISTS。字符串比较区分大小写。

### Action

| 属性 | 用途与约束 |
|---|---|
| `type` | 必填动作类型，见下表。 |
| `mode` | REQUIRE_APPROVAL/APPEND_APPROVAL必填：SERIAL串行、PARALLEL同节点并行；其他动作省略。 |
| `targets` | 审批/抄送动作必填，1到100个不重复的ROLE:角色编码或USER:用户ID；其他动作省略。 |
| `key` | SET_RESULT必填输出键；字母/下划线开头，后续可含数字，最多64字符，使用results顶层键；业务方通过results.key读取。 |
| `value` | SET_RESULT必填固定JSON值；可为标量、数组、对象或显式null。支持类型不涉及Java表达式求值。 |

`SET_RESULT` 是声明式赋值：命中后由引擎收集结果并写入 `results`。例如订单金额不超过100时设 `score=0`，较高金额时设 `score=1`，业务方据此解释审批层级；实际审批人和审批流仍由 `REQUIRE_APPROVAL` 等动作生成到 `plan`。当前通用事实是只读的 `GenericFact`，生成的DRL通过 `GenericRuleResult` 汇总动作，因此任意Java对象的 `$s.setScore(0); update($s);` 不受支持，也不会执行 `update` 后的重新匹配。若必须使用此语法，需要引入具体类型的事实对象及有状态会话更新语义。
| `reason` | REJECT必填拒绝原因，非空、最多2000字符；其他动作省略。 |

| type | 配置 | 行为 |
|---|---|---|
| AUTO_APPROVE | 仅type | 基础自动通过，只能用于BASE |
| REQUIRE_APPROVAL | mode + targets | 基础人工审批，只能用于BASE |
| APPEND_APPROVAL | mode + targets | 增加审批节点 |
| CC_AFTER_APPROVAL | targets | 审批通过后的抄送对象，去重 |
| REJECT | reason | 最终decision为REJECTED，原因合并至reasons |
| SET_RESULT | key + value | 输出固定业务结果，相同key采用最高优先级有效规则的值；同优先级按配置顺序，同规则取第一项 |

同一规则最多一个AUTO_APPROVE或REQUIRE_APPROVAL动作。审批节点先采用BASE，再按优先级降序采用SUPPLEMENT，同优先级按配置顺序。命中多个BASE时，未采用的BASE不会贡献动作及matchedRules；matchedRules表示实际采用的规则。

结果决策：REJECTED优先；无基础命中为NO_MATCH；有基础且有审批节点为MANUAL_APPROVAL；有基础自动动作为AUTO_APPROVE；其他基础命中为MATCHED。仅做业务结果计算可使用BASE + SET_RESULT。响应新增results对象和reasons数组，plan保留审批结果；通用规则未使用旧品类配置时policyId/policyName为null。拒绝时仍返回命中的审批计划供追踪，业务系统应按decision拒绝单据，不启动审批。

原价格policies格式继续接受，但不能与fields/rules混用。通用模型不自动推断区间连续性，业务方按priority和条件配置处理范围；兼容价格模型仍保留区间覆盖/重叠检查。

商品、订单及其他单据统一通过`POST /api/rules/execute`执行。执行请求把业务字段放在document.data中；配置的calculation声明受控计算方式，规则引擎计算后再匹配同一规则集下的区间与风控规则。

### PriceRuleConfig（兼容原价格页面）

| 属性 | 用途与约束 |
|---|---|
| `ruleName` | 规则集标识，发布配置的JSON字段为 ruleName；必须与URL中的{name}一致。业务方执行时通过 rule_name 引用此标识。必填；1到64个字母、数字、下划线、冒号或短横线。 |
| `businessType` | 规则业务类型；必填。SALES_PRICE=销售价，COST_PRICE=成本调价；用于说明该规则集的业务类型。 |
| `calculation` | 页面选择的受控计算方式及输入/结果字段。销售使用GROSS_MARGIN，成本调价使用COST_INCREASE；可省略以兼容旧配置。 |
| `policies` | 品类策略列表；必填，1到100组。必须有且只有一个默认策略；每组包含基础区间和可叠加的风控规则。 |

### Policy

| 属性 | 用途与约束 |
|---|---|
| `id` | 品类策略的唯一标识，例如 default、custom1；在当前规则集内不能重复，并用于生成DRL名称和返回命中策略。必填；1到64个字母、数字、下划线、冒号或短横线。 |
| `name` | 品类策略展示名称；必填、非空，例如 默认规则；用于页面展示和返回policyName。 |
| `defaultPolicy` | 是否为兜底策略；true表示默认策略，categoryIds必须为空；false表示自定义品类策略，categoryIds必须至少有一个ID。 |
| `categoryIds` | 该策略适用的品类ID列表；必填。默认策略传[]；自定义策略至少指定一个品类ID，每个ID符合标识格式。同一品类ID不能配置到多组策略，匹配时包含其子品类。 |
| `bands` | 基础审批区间列表；必填，1到100条，按下限升序排列且不能重叠。销售价区间必须连续覆盖全部毛利率范围。 |
| `risks` | 风控规则列表；必填，允许[]，最多100条。命中时叠加到基础审批计划，多条命中按配置顺序追加审批并合并抄送。 |

### Band

| 属性 | 用途与约束 |
|---|---|
| `id` | 基础区间的唯一标识，例如 band1；与本策略中的其他区间和风控ID不能重复。必填；1到64个字母、数字、下划线、冒号或短横线。 |
| `min` | 区间下限，单位为百分数；null表示无下限，5表示5%。销售规则对应毛利率，成本规则对应上调幅度。 |
| `minInclusive` | 是否包含下限；true对应≥，false对应>。min为null时此字段不影响匹配。 |
| `max` | 区间上限，单位为百分数；null表示无上限，10表示10%。有上下限时min必须小于max。 |
| `maxInclusive` | 是否包含上限；true对应小于等于，false对应小于。max为null时此字段不影响匹配。 |
| `autoApprove` | 是否无需人工审批；true时approvers必须为空，false时必须配置审批人。命中风控追加审批时，最终结果仍会要求人工审批。 |
| `mode` | 基础审批方式；必填。SERIAL按approvers顺序生成串行节点；PARALLEL将所有审批人放入同一个并行节点。 |
| `approvers` | 基础审批人选择器列表；必填，最多100个。ROLE:角色编码或USER:用户ID，例如 ROLE:PURCHASE_PLUS_1、USER:DEMO_APPROVER。实际人员由业务系统结合单据解析。 |

### Risk

| 属性 | 用途与约束 |
|---|---|
| `id` | 风控规则的唯一标识，例如 risk1；与本策略中的其他风控和基础区间ID不能重复。必填；1到64个字母、数字、下划线、冒号或短横线。 |
| `operator` | 阈值比较符；必填。LT表示小于，LE表示小于等于，EQ表示等于，GE表示大于等于，GT表示大于；与threshold共同判断是否触发风控。 |
| `threshold` | 风控触发阈值；必填，单位为百分数，例如-50表示-50%。销售规则比较毛利率，成本规则比较上调幅度。 |
| `mode` | 追加审批方式；必填。SERIAL按additionalApprovers顺序串行追加；PARALLEL追加一个包含所有指定审批人的并行节点。 |
| `additionalApprovers` | 风控命中时追加的审批人选择器；必填，允许[]，最多100个，格式与approvers相同。追加节点排在基础审批节点之后。 |
| `ccAfterApproval` | 风控命中时在审批通过后抄送的对象选择器；必填，允许[]，最多100个，格式与approvers相同。多条风控的抄送对象会去重，由业务系统在审批通过后发送。 |

## 共用Java模型

| 共用类 | 使用位置 | 共用属性 |
|---|---|---|
| `RuleService.ExecutionRequest` | 通用规则执行请求 | rule_name、可选version、document |
| `ApiModels.RuleVersion` | 发布/查询响应 | 规则名和确定的version；发布/查询仍使用ruleName |
| `ApiModels.RuleOutcome` | 通用规则执行响应 | plan、results、reasons |
| `ParameterDefinition` | fields入参、outputs业务出参 | name、type、required、description、example |
| `ApiModels.Problem` | 当前接口的400/404/执行及存储500响应 | type、title、status、detail |

继承复用保持原有JSON字段结构，业务方无需传入额外的context或metadata层。不同业务的专属字段继续由对应模型表达。

## 发布/查询出参：RuleStore.Version

| 属性 | 含义 |
|---|---|
| `ruleName` | 当前规则集标识，沿用发布/查询接口camelCase字段名。 |
| `version` | 本次发布或查询的确定版本号。 |
| `configJson` | 完整配置的JSON字符串；包含条件、动作及完整入出参定义、用途说明、示例、确定来源版本。页面读取后解析一次JSON即可编辑。 |
| `drl` | 与configJson同版本的已编译校验DRL快照。 |

## 通用执行出参：ExecutionResult

所有业务类型的执行响应共用此模型。

| 属性 | 含义 |
|---|---|
| `executionId` | 本次执行UUID，用于检索应用INFO执行日志。重复执行同一单据产生不同ID。 |
| `rule_name` | 实际执行的规则集标识。 |
| `version` | 实际执行的确定版本；执行日志使用同一版本。 |
| `calculations` | 引擎按当前版本受控计算选项计算出的指标对象，例如`{"grossMarginPct":10}`；未配置calculation时为空对象。 |
| `policyId` | 旧价格配置匹配的品类策略ID；通用配置为null。 |
| `policyName` | 旧价格配置的品类策略名称；通用配置为null。 |
| `plan` | 审批计划及规则决策，结构见下表。 |
| `results` | SET_RESULT产生的业务结果对象；声明outputs时按当前版本定义检查键、类型及必填项。无业务结果时为{}。 |
| `reasons` | 命中REJECT动作时去重后的拒绝原因数组；其他情况为[]。 |

### ApprovalPlan.Result

| 属性 | 含义 |
|---|---|
| `decision` | AUTO_APPROVE自动通过、MANUAL_APPROVAL人工审批、REJECTED拒绝、NO_MATCH未命中基础规则、MATCHED仅计算业务结果/记录命中。 |
| `approvalSteps` | 按顺序排列的审批节点数组；由业务流程系统执行，拒绝时不启动流程。 |
| `ccAfterApproval` | 审批通过后的抄送对象选择器数组，合并去重；当前计算不发送抄送。 |
| `matchedRules` | 实际采用的完整规则ID列表，BASE在前、SUPPLEMENT按优先级及配置顺序在后。未采用的BASE不计入。 |

### ApprovalPlan.Step

| 属性 | 含义 |
|---|---|
| `order` | 从1开始的节点序号。 |
| `mode` | SERIAL串行单对象节点或PARALLEL同节点多对象审批。 |
| `approvers` | 审批对象选择器数组，ROLE:角色编码或USER:用户ID，业务系统负责解析真实人员。 |
| `sourceRule` | 产生节点的ruleName:ruleId，用于追踪审批来源。 |

## 整体错误出参：ApiModels.Problem

| 属性 | 含义 |
|---|---|
| `type` | 当前about:blank，以HTTP状态说明错误类型。 |
| `title` | HTTP状态文字说明，如Bad Request、Not Found、Internal Server Error。 |
| `status` | HTTP状态码：400入参/配置错误，404规则或版本不存在，500执行/出参契约/存储故障。 |
| `detail` | 错误的具体说明；服务端故障返回统一说明，详细异常写入服务端日志。 |

## 规则查询：GET /api/rules/{name}

| 参数 | 位置 | 用途 |
|---|---|---|
| `name` | URL路径 | 必填，规则集标识，例如sales_price。 |
| `version` | 查询参数 | 可选，已发布版本号；不传时读取当前生效版本。例如`?version=1`。 |

## 调用约定

商品、订单及其他业务统一通过`POST /api/rules/execute`执行；业务方传入本业务已注册的`rule_name`及一份单据数据。发布接口配置版本中的规则集包含全部基础规则及风控规则。需要处理多个商品时逐商品调用，每次返回一个ExecutionResult。
规则发布接口使用`ruleName`，执行接口使用`rule_name`。商品销售价请求见[销售价请求](examples/execute-sales.json)，成本调价请求见[成本调价请求](examples/execute-cost.json)，配置分别见[销售规则](examples/sales-config.json)和[成本规则](examples/cost-config.json)。订单请求见[订单请求](examples/execute-order.json)，订单配置见[订单配置](examples/order-config.json)。
