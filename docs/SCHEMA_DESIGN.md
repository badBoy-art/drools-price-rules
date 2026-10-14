# 表结构与规则实现

表结构以 `src/main/resources/schema.sql` 为准。`ac_audit_rule` 以 `rule_code` 标识一份配置，`ac_audit_rule_cond.rule_id` 关联主表 `id` 并保存全部规则条件。接口中的 `businessCode/businessName` 分别映射到主表的 `rule_code/rule_name`。

页面的品类毛利区间、品类毛利风控和成本上调规则统一存为 `ac_audit_rule_cond` 条件行，类型分别为 `CATEGORY_INTERVAL`、`CATEGORY_RISK`、`COST_INCREASE`。相同 `rule_name` 的行构成一个规则组；组内条件以 `cond_code` 标识，`rule_sort_no` 和 `cond_sort_no` 保存规则组及条件顺序。`category_id1/2/3` 是 bigint，条件范围按已设置的品类层级匹配。

接口条件字段到数据库字段的映射为：`conditionCode/conditionType/valueField/predicateType/lowerBound/lowerInclusive/upperBound/upperInclusive/compType/thresholdVal/resultAssignments/scriptContent/conditionSort` 对应 `cond_code/cond_type_name/value_field_name/judge_type_name/lowbound_val/is_inc_lowbound/upbound_val/is_inc_upbound/cmpr_type/thres/rslt_assign_info/script_cont/cond_sort_no`。结果 JSON 可配置多项 `{field,value,when}` 赋值，路径支持多级对象。

创建和更新在事务中按完整配置生成并校验每条条件的 QLExpress 片段，保存至 `script_cont`。执行时查询有效条件，拼接片段判断命中，再将命中条件的赋值写入输入副本。所有条件使用原始输入判断。主表 `rule_code` 是调用方选择配置的键；条件表以 `(rule_id, cond_code)` 唯一标识条件。`del_flag` 用于逻辑删除。
