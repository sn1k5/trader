-- ============================================================
-- Java-Admin 独立建表语句
-- 仅包含 Java-Admin JPA 实体对应的表定义
-- 字段类型和约束与 Java 实体 @Column 注解完全一致
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 一、用户与认证模块
-- ============================================================

CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名，用于登录，唯一',
  `password` VARCHAR(128) NOT NULL COMMENT '密码哈希值',
  `role` VARCHAR(20) NOT NULL DEFAULT 'TRADER' COMMENT '角色：ADMIN/TRADER/VIEWER',
  `status` INT NOT NULL DEFAULT 1 COMMENT '状态：0=禁用, 1=正常',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`) COMMENT '用户名唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE `trading_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id',
  `account_no` VARCHAR(32) NOT NULL COMMENT '账户编号，业务唯一标识',
  `status` INT NOT NULL DEFAULT 1 COMMENT '状态：0=禁用, 1=正常',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_no` (`account_no`) COMMENT '账户编号唯一索引',
  KEY `idx_user_id` (`user_id`) COMMENT '按用户查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易账户表';

-- ============================================================
-- 二、持仓模块
-- ============================================================

CREATE TABLE `position` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id',
  `symbol_id` INT NOT NULL COMMENT '交易对内部ID，对应C++ Symbol.Id',
  `side` INT NOT NULL COMMENT '持仓方向：0=多仓, 1=空仓',
  `quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '持仓数量（定点数）',
  `avg_price` BIGINT NOT NULL DEFAULT 0 COMMENT '开仓均价（定点数）',
  `unrealized_pnl` BIGINT NOT NULL DEFAULT 0 COMMENT '未实现盈亏（定点数）',
  `updated_at` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_symbol_side` (`user_id`, `symbol_id`, `side`) COMMENT '用户+交易对+方向唯一索引',
  KEY `idx_user_id` (`user_id`) COMMENT '按用户查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='持仓表';

-- ============================================================
-- 三、订单与成交模块
-- ============================================================

CREATE TABLE `order_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id',
  `order_id` BIGINT NOT NULL COMMENT '订单ID，对应C++ Order.Id',
  `symbol_id` INT NOT NULL COMMENT '交易对内部ID',
  `side` INT NOT NULL COMMENT '买卖方向：0=买, 1=卖',
  `order_type` INT NOT NULL COMMENT '订单类型：0=MARKET, 1=LIMIT, 2=STOP, 3=STOP_LIMIT, 4=TRAILING_STOP, 5=TRAILING_STOP_LIMIT',
  `price` BIGINT NOT NULL COMMENT '委托价格（定点数）',
  `quantity` BIGINT NOT NULL COMMENT '委托数量（定点数）',
  `executed_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '已成交数量（定点数）',
  `status` INT NOT NULL COMMENT '订单状态：0=NEW, 1=PARTIAL_FILLED, 2=FILLED, 3=CANCELED, 4=REJECTED, 5=PENDING_STOP',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_created_at` (`user_id`, `created_at`) COMMENT '用户订单按时间查询复合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单历史表';

CREATE TABLE `execution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id',
  `order_id` BIGINT NOT NULL COMMENT '订单ID，关联order_history.order_id',
  `symbol_id` INT NOT NULL COMMENT '交易对内部ID',
  `price` BIGINT NOT NULL COMMENT '成交价格（定点数）',
  `quantity` BIGINT NOT NULL COMMENT '成交数量（定点数）',
  `side` INT NOT NULL COMMENT '买卖方向：0=买, 1=卖',
  `executed_at` DATETIME NOT NULL COMMENT '成交时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_executed_at` (`user_id`, `executed_at`) COMMENT '用户成交按时间查询复合索引',
  KEY `idx_order_id_executed_at` (`order_id`, `executed_at`) COMMENT '订单成交按时间查询复合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成交记录表';

-- ============================================================
-- 四、资产余额模块
-- ============================================================

CREATE TABLE `account_balance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id，唯一',
  `available` DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
  `frozen` DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '冻结余额',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`) COMMENT '用户ID唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账户余额表';

CREATE TABLE `balance_change_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `change_id` VARCHAR(64) NOT NULL COMMENT '变更ID，UUID，用于幂等去重',
  `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id',
  `amount` DECIMAL(20,2) NOT NULL COMMENT '变更金额，正数增加，负数减少',
  `type` INT NOT NULL COMMENT '变更类型：1=扣款, 2=充值, 3=冻结, 4=解冻',
  `biz_id` VARCHAR(64) DEFAULT NULL COMMENT '业务ID，关联业务订单号',
  `status` INT NOT NULL DEFAULT 0 COMMENT '变更状态：0=处理中, 1=已确认',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_change_id` (`change_id`) COMMENT '变更ID唯一索引',
  KEY `idx_user_id` (`user_id`) COMMENT '按用户查询索引',
  KEY `idx_biz_id` (`biz_id`) COMMENT '按业务ID查询索引',
  KEY `idx_created_at` (`created_at`) COMMENT '按时间查询索引',
  KEY `idx_type_status` (`type`, `status`) COMMENT '类型和状态联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余额变更日志表';

-- ============================================================
-- 五、幂等与去重模块
-- ============================================================

CREATE TABLE `idempotent_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` VARCHAR(64) NOT NULL COMMENT '消息ID，全局唯一',
  `consumer_group` VARCHAR(64) NOT NULL COMMENT '消费者组名',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_consumer` (`message_id`, `consumer_group`) COMMENT '消息ID+消费者组唯一索引',
  KEY `idx_created_at` (`created_at`) COMMENT '按创建时间查询索引，用于清理过期记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等记录表，用于消息去重';

-- ============================================================
-- 六、消息出站模块（Outbox Pattern）
-- ============================================================

CREATE TABLE `outbox_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` VARCHAR(64) NOT NULL COMMENT '消息ID，UUID，全局唯一',
  `topic` VARCHAR(128) NOT NULL COMMENT '消息主题',
  `payload` TEXT COMMENT '消息载荷，JSON格式',
  `status` INT NOT NULL DEFAULT 0 COMMENT '状态：0=PENDING, 1=SENT, 2=CONSUMED, 3=DEAD',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `max_retry` INT NOT NULL DEFAULT 10 COMMENT '最大重试次数',
  `next_retry_time` DATETIME NOT NULL COMMENT '下次重试时间',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`) COMMENT '消息ID唯一索引',
  KEY `idx_status_next_retry` (`status`, `next_retry_time`) COMMENT '按状态和重试时间查询索引',
  KEY `idx_status` (`status`) COMMENT '按状态查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息出站表，Outbox Pattern实现';

-- ============================================================
-- 七、风控模块
-- ============================================================

CREATE TABLE `risk_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_name` VARCHAR(64) NOT NULL COMMENT '规则名称',
  `rule_type` VARCHAR(32) NOT NULL COMMENT '规则类型：SINGLE_LIMIT/DAILY_LIMIT/POSITION_LIMIT/FREQ_LIMIT',
  `params` TEXT NOT NULL COMMENT '规则参数，JSON格式',
  `enabled` INT NOT NULL DEFAULT 1 COMMENT '启用状态：0=禁用, 1=启用',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_enabled` (`enabled`) COMMENT '按启用状态查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风控规则配置表';

CREATE TABLE `risk_alert` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id',
  `rule_name` VARCHAR(64) NOT NULL COMMENT '触发的规则名称',
  `detail` TEXT COMMENT '告警详情',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_created_at` (`user_id`, `created_at`) COMMENT '用户告警按时间查询复合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风控告警表';

-- ============================================================
-- 八、Saga 分布式事务模块
-- ============================================================

CREATE TABLE `saga_instance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `saga_name` VARCHAR(128) NOT NULL COMMENT 'Saga名称',
  `saga_id` VARCHAR(64) NOT NULL COMMENT 'Saga实例ID，UUID，全局唯一',
  `current_step` INT NOT NULL DEFAULT 0 COMMENT '当前步骤索引',
  `status` INT NOT NULL DEFAULT 0 COMMENT '状态：0=RUNNING, 1=COMPLETED, 2=COMPENSATING, 3=COMPENSATED, 4=COMPENSATE_FAILED',
  `context` TEXT COMMENT 'Saga上下文数据，JSON格式',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saga_id` (`saga_id`) COMMENT 'Saga实例ID唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Saga实例表';

CREATE TABLE `saga_step_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `saga_id` VARCHAR(64) NOT NULL COMMENT 'Saga实例ID，关联saga_instance.saga_id',
  `step_name` VARCHAR(128) NOT NULL COMMENT '步骤名称',
  `step_order` INT NOT NULL COMMENT '步骤顺序',
  `status` INT NOT NULL DEFAULT 0 COMMENT '步骤状态：0=PENDING, 1=SUCCESS, 2=FAILED, 3=COMPENSATED, 4=COMPENSATE_FAILED',
  `request_data` TEXT COMMENT '请求数据，JSON格式',
  `response_data` TEXT COMMENT '响应数据，JSON格式',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_saga_id_step_order` (`saga_id`, `step_order`) COMMENT 'Saga实例+步骤顺序复合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Saga步骤日志表';

-- ============================================================
-- 九、对账模块
-- ============================================================

CREATE TABLE `reconcile_diff_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id',
  `redis_available` DECIMAL(20,2) NOT NULL COMMENT 'Redis中的可用余额',
  `mysql_available` DECIMAL(20,2) NOT NULL COMMENT 'MySQL中的可用余额',
  `diff_amount` DECIMAL(20,2) NOT NULL COMMENT '差异金额 = redis_available - mysql_available',
  `fixed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已修复：0=未修复, 1=已修复',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_fixed` (`fixed`) COMMENT '按修复状态查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账差异记录表';

SET FOREIGN_KEY_CHECKS = 1;
