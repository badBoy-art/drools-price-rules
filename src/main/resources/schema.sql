-- 每个rule_name的最大version为当前生效版本，历史版本继续保留。
CREATE TABLE rule_version (
  rule_name    VARCHAR(64) NOT NULL COMMENT '规则集标识',
  version      BIGINT NOT NULL COMMENT '规则版本号，同一规则集从1开始递增',
  config_json  LONGTEXT NOT NULL COMMENT '规则及完整入出参数定义、共用参数来源版本的JSON快照',
  drl_content  LONGTEXT NOT NULL COMMENT '编译校验通过的DRL快照',
  creator_id   VARCHAR(36) DEFAULT '' NOT NULL COMMENT '创建人id',
  created_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  updater_id   VARCHAR(36) DEFAULT '' NOT NULL COMMENT '最后更新人id',
  updated_date DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (rule_name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则配置及历史版本';
