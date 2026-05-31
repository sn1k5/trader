-- =============================================
-- CppTrader Java Admin 数据库表结构
-- 数据库: cpptrader
-- 字符集: utf8mb4
-- 排序规则: utf8mb4_unicode_ci
-- =============================================

-- =============================================
-- 1. 用户系统表
-- =============================================

-- ---------------------------------------------
-- 系统用户表 (sys_user)
-- 用途：存储系统用户的基本信息和认证信息
-- ---------------------------------------------
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名，唯一标识一个用户，用于登录和识别',
    `password` VARCHAR(128) NOT NULL COMMENT '密码，加密存储的用户密码',
    `role` VARCHAR(20) NOT NULL DEFAULT 'TRADER' COMMENT '用户角色：TRADER-交易员, ADMIN-管理员等',
    `status` INT NOT NULL DEFAULT 1 COMMENT '用户状态：0-禁用, 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，用户注册时设置',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间，用户信息变更时自动更新',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`) COMMENT '用户名唯一索引，确保用户名不重复'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表，存储用户的基本信息和认证信息';

-- ---------------------------------------------
-- 交易账户表 (trading_account)
-- 用途：存储用户的交易账户信息
-- ---------------------------------------------
CREATE TABLE `trading_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user表的用户',
    `account_no` VARCHAR(32) NOT NULL COMMENT '账户编号，唯一的交易账户标识符',
    `status` INT NOT NULL DEFAULT 1 COMMENT '账户状态：0-禁用, 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，账户开立时设置',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_no` (`account_no`) COMMENT '账户编号唯一索引，确保账户编号不重复',
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，加速按用户查询账户'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易账户表，存储用户的交易账户信息';

-- =============================================
-- 2. 余额相关表
-- =============================================

-- ---------------------------------------------
-- 账户余额表 (account_balance)
-- 用途：存储用户账户的可用余额和冻结余额信息
-- ---------------------------------------------
CREATE TABLE `account_balance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，唯一标识一个用户账户',
    `available` DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额，用户可以自由使用的金额',
    `frozen` DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '冻结余额，因交易或其他原因被暂时锁定的金额',
    `version` INT NOT NULL DEFAULT 0 COMMENT '版本号，用于乐观锁控制，防止并发更新冲突',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，首次创建账户时设置',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间，每次数据变更时自动更新',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`) COMMENT '用户ID唯一索引，确保每个用户只有一个余额记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账户余额表，存储用户账户的可用余额和冻结余额';

-- ---------------------------------------------
-- 余额变更日志表 (balance_change_log)
-- 用途：记录所有余额变更操作的详细信息，用于审计和追踪
-- ---------------------------------------------
CREATE TABLE `balance_change_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `change_id` VARCHAR(64) NOT NULL COMMENT '变更ID，全局唯一标识符(UUID)，用于幂等性控制和去重',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，标识发生余额变更的用户',
    `amount` DECIMAL(20,2) NOT NULL COMMENT '变更金额，正数表示增加，负数表示减少',
    `type` INT NOT NULL COMMENT '变更类型：1-扣款(DEDUCT), 2-充值(ADD), 3-冻结(FREEZE), 4-解冻(UNFREEZE)',
    `biz_id` VARCHAR(64) DEFAULT NULL COMMENT '业务ID，关联的具体业务订单或操作ID，如订单号、交易号等',
    `status` INT NOT NULL DEFAULT 0 COMMENT '变更状态：0-处理中(PROCESSING), 1-已确认(CONFIRMED)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，余额变更发生的时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_change_id` (`change_id`) COMMENT '变更ID唯一索引，确保每条变更记录的唯一性，防止重复处理',
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，加速按用户查询余额变更日志'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余额变更日志表，记录所有余额变动的详细信息，用于审计和追踪';

-- =============================================
-- 3. 订单和持仓表
-- =============================================

-- ---------------------------------------------
-- 订单历史表 (order_history)
-- 用途：存储订单的历史记录，包括已成交和已取消的订单
-- ---------------------------------------------
CREATE TABLE `order_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，下单用户的标识',
    `order_id` BIGINT NOT NULL COMMENT '订单ID，唯一标识一个订单',
    `symbol_id` INT NOT NULL COMMENT '交易品种ID，标识交易的标的物',
    `side` INT NOT NULL COMMENT '买卖方向：0-买入(BUY), 1-卖出(SELL)',
    `order_type` INT NOT NULL COMMENT '订单类型：0-市价单(MARKET), 1-限价单(LIMIT), 2-止损单(STOP), 3-止损限价单(STOP_LIMIT), 4-跟踪止损单(TRAILING_STOP), 5-跟踪止损限价单(TRAILING_STOP_LIMIT)',
    `price` BIGINT NOT NULL COMMENT '订单价格，以最小价格单位表示（如分）',
    `quantity` BIGINT NOT NULL COMMENT '订单数量，要交易的标的数量',
    `executed_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '已成交数量，已经执行的订单数量',
    `status` INT NOT NULL COMMENT '订单状态：0-新建(PENDING_NEW), 1-已接受(ACCEPTED), 2-部分成交(PARTIALLY_FILLED), 3-完全成交(FILLED), 4-已取消(CANCELLED), 5-已拒绝(REJECTED), 6-已过期(EXPIRED)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，订单创建时设置',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间，订单状态变更时自动更新',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，加速按用户查询订单历史',
    KEY `idx_order_id` (`order_id`) COMMENT '订单ID索引，加速按订单ID查询',
    KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引，加速按时间范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单历史表，存储订单的完整历史记录';

-- ---------------------------------------------
-- 成交记录表 (execution)
-- 用途：存储每笔成交的详细信息
-- ---------------------------------------------
CREATE TABLE `execution` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，成交用户的标识',
    `order_id` BIGINT NOT NULL COMMENT '订单ID，关联的订单',
    `symbol_id` INT NOT NULL COMMENT '交易品种ID，成交的标的物',
    `price` BIGINT NOT NULL COMMENT '成交价格，以最小价格单位表示（如分）',
    `quantity` BIGINT NOT NULL COMMENT '成交数量，本次成交的数量',
    `side` INT NOT NULL COMMENT '买卖方向：0-买入(BUY), 1-卖出(SELL)',
    `executed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成交时间，成交发生的时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，加速按用户查询成交记录',
    KEY `idx_order_id` (`order_id`) COMMENT '订单ID索引，加速按订单查询成交记录',
    KEY `idx_executed_at` (`executed_at`) COMMENT '成交时间索引，加速按时间范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成交记录表，存储每笔成交的详细信息';

-- ---------------------------------------------
-- 持仓表 (position)
-- 用途：存储用户的持仓信息
-- ---------------------------------------------
CREATE TABLE `position` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，持仓用户的标识',
    `symbol_id` INT NOT NULL COMMENT '交易品种ID，持仓的标的物',
    `side` INT NOT NULL COMMENT '持仓方向：0-多头(LONG), 1-空头(SHORT)',
    `quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '持仓数量，当前持有的标的数量',
    `avg_price` BIGINT NOT NULL DEFAULT 0 COMMENT '平均成本价，以最小价格单位表示（如分）',
    `unrealized_pnl` BIGINT NOT NULL DEFAULT 0 COMMENT '未实现盈亏，以最小价格单位表示（如分），正数表示盈利，负数表示亏损',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间，持仓变化时自动更新',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_symbol_side` (`user_id`, `symbol_id`, `side`) COMMENT '用户-品种-方向唯一索引，确保每个用户在每个品种的每个方向只有一个持仓记录',
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，加速按用户查询持仓'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='持仓表，存储用户的持仓信息';

-- =============================================
-- 4. 风控相关表
-- =============================================

-- ---------------------------------------------
-- 风控规则表 (risk_rule)
-- 用途：存储风险控制规则配置
-- ---------------------------------------------
CREATE TABLE `risk_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `rule_name` VARCHAR(64) NOT NULL COMMENT '规则名称，描述风控规则的名称',
    `rule_type` VARCHAR(32) NOT NULL COMMENT '规则类型：SINGLE_LIMIT-单笔限额, DAILY_LIMIT-日累计限额, POSITION_LIMIT-持仓限额, FREQ_LIMIT-频率限制等',
    `params` TEXT COMMENT '规则参数，JSON格式存储规则的具体参数配置',
    `enabled` INT NOT NULL DEFAULT 1 COMMENT '规则状态：0-禁用, 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，规则创建时设置',
    PRIMARY KEY (`id`),
    KEY `idx_rule_type` (`rule_type`) COMMENT '规则类型索引，加速按类型查询规则'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风控规则表，存储风险控制规则配置';

-- ---------------------------------------------
-- 风控告警表 (risk_alert)
-- 用途：存储触发风控规则的告警记录
-- ---------------------------------------------
CREATE TABLE `risk_alert` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，触发风控的用户',
    `rule_name` VARCHAR(64) NOT NULL COMMENT '规则名称，触发的风控规则名称',
    `detail` TEXT COMMENT '告警详情，JSON格式存储告警的详细信息，包括触发条件、当前值等',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，告警触发时设置',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，加速按用户查询告警',
    KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引，加速按时间范围查询告警'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风控告警表，存储触发风控规则的告警记录';

-- =============================================
-- 5. 一致性相关表（Outbox Pattern）
-- =============================================

-- ---------------------------------------------
-- Outbox消息表 (outbox_message)
-- 用途：存储待发送的消息，用于实现可靠消息传递
-- ---------------------------------------------
CREATE TABLE `outbox_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `message_id` VARCHAR(64) NOT NULL COMMENT '消息ID，全局唯一标识符(UUID)，用于消息去重和追踪',
    `topic` VARCHAR(128) NOT NULL COMMENT '消息主题，标识消息的目标队列或主题',
    `payload` TEXT COMMENT '消息内容，JSON格式存储消息的实际数据',
    `status` INT NOT NULL DEFAULT 0 COMMENT '消息状态：0-待发送(PENDING), 1-已发送(SENT), 2-已消费(CONSUMED), 3-死信(DEAD)',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数，消息发送失败后的重试次数',
    `max_retry` INT NOT NULL DEFAULT 10 COMMENT '最大重试次数，超过此次数后消息将标记为死信',
    `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间，消息失败后下次尝试发送的时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，消息创建时设置',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间，消息状态变更时自动更新',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`) COMMENT '消息ID唯一索引，确保消息ID不重复',
    KEY `idx_status_next_retry` (`status`, `next_retry_time`) COMMENT '状态和重试时间联合索引，加速定时任务扫描待重试消息',
    KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引，加速按时间范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox消息表，存储待发送的消息，用于实现可靠消息传递';

-- =============================================
-- 6. Saga分布式事务相关表
-- =============================================

-- ---------------------------------------------
-- Saga实例表 (saga_instance)
-- 用途：存储Saga分布式事务的实例信息
-- ---------------------------------------------
CREATE TABLE `saga_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `saga_name` VARCHAR(128) NOT NULL COMMENT 'Saga名称，标识Saga流程的类型',
    `saga_id` VARCHAR(64) NOT NULL COMMENT 'Saga ID，全局唯一标识符(UUID)，唯一标识一个Saga实例',
    `current_step` INT NOT NULL DEFAULT 0 COMMENT '当前步骤，Saga流程执行到的步骤序号',
    `status` INT NOT NULL DEFAULT 0 COMMENT 'Saga状态：0-运行中(RUNNING), 1-已完成(COMPLETED), 2-补偿中(COMPENSATING), 3-已补偿(COMPENSATED), 4-补偿失败(COMPENSATE_FAILED)',
    `context` TEXT COMMENT 'Saga上下文，JSON格式存储Saga执行过程中的共享数据',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，Saga启动时设置',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间，Saga状态变更时自动更新',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saga_id` (`saga_id`) COMMENT 'Saga ID唯一索引，确保Saga ID不重复',
    KEY `idx_status` (`status`) COMMENT '状态索引，加速按状态查询Saga实例',
    KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引，加速按时间范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Saga实例表，存储Saga分布式事务的实例信息';

-- ---------------------------------------------
-- Saga步骤日志表 (saga_step_log)
-- 用途：存储Saga流程中每个步骤的执行日志
-- ---------------------------------------------
CREATE TABLE `saga_step_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `saga_id` VARCHAR(64) NOT NULL COMMENT 'Saga ID，关联的Saga实例',
    `step_name` VARCHAR(128) NOT NULL COMMENT '步骤名称，标识Saga中的具体步骤',
    `step_order` INT NOT NULL COMMENT '步骤顺序，步骤在Saga流程中的执行顺序',
    `status` INT NOT NULL DEFAULT 0 COMMENT '步骤状态：0-待执行(PENDING), 1-成功(SUCCESS), 2-失败(FAILED), 3-已补偿(COMPENSATED), 4-补偿失败(COMPENSATE_FAILED)',
    `request_data` TEXT COMMENT '请求数据，JSON格式存储步骤执行时的输入参数',
    `response_data` TEXT COMMENT '响应数据，JSON格式存储步骤执行后的返回结果',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，步骤执行时设置',
    PRIMARY KEY (`id`),
    KEY `idx_saga_id` (`saga_id`) COMMENT 'Saga ID索引，加速按Saga查询步骤日志',
    KEY `idx_saga_order` (`saga_id`, `step_order`) COMMENT 'Saga ID和步骤顺序联合索引，加速按顺序查询步骤'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Saga步骤日志表，存储Saga流程中每个步骤的执行日志';

-- =============================================
-- 7. 幂等性相关表
-- =============================================

-- ---------------------------------------------
-- 幂等记录表 (idempotent_record)
-- 用途：存储消息处理的幂等性记录，防止重复处理
-- ---------------------------------------------
CREATE TABLE `idempotent_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `message_id` VARCHAR(64) NOT NULL COMMENT '消息ID，需要保证幂等处理的消息标识',
    `consumer_group` VARCHAR(64) NOT NULL COMMENT '消费者组，处理消息的消费者组名称',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，消息首次处理时设置',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_consumer` (`message_id`, `consumer_group`) COMMENT '消息ID和消费者组唯一索引，确保同一消息在同一消费者组只处理一次'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等记录表，存储消息处理的幂等性记录，防止重复处理';

-- =============================================
-- 8. 对账相关表
-- =============================================

-- ---------------------------------------------
-- 对账差异记录表 (reconcile_diff_record)
-- 用途：存储Redis和MySQL之间余额对账发现的差异记录
-- ---------------------------------------------
CREATE TABLE `reconcile_diff_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，存在余额差异的用户',
    `redis_available` DECIMAL(20,2) NOT NULL COMMENT 'Redis可用余额，Redis中记录的用户可用余额',
    `mysql_available` DECIMAL(20,2) NOT NULL COMMENT 'MySQL可用余额，MySQL中记录的用户可用余额',
    `diff_amount` DECIMAL(20,2) NOT NULL COMMENT '差异金额，Redis和MySQL余额的差值（Redis - MySQL）',
    `fixed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已修复：0-未修复, 1-已修复',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，对账发现差异时设置',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，加速按用户查询差异记录',
    KEY `idx_fixed` (`fixed`) COMMENT '修复状态索引，加速查询未修复的差异',
    KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引，加速按时间范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账差异记录表，存储Redis和MySQL之间余额对账发现的差异记录';

-- =============================================
-- 9. 自成交防范相关表
-- =============================================

-- ---------------------------------------------
-- 自成交防范配置表 (stp_config)
-- 用途：存储自成交防范策略的配置，支持按交易品种配置不同策略
-- ---------------------------------------------
CREATE TABLE `stp_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `symbol_id` INT DEFAULT NULL COMMENT '交易品种ID，NULL表示全局默认配置，非NULL表示品种级配置',
    `policy` VARCHAR(32) NOT NULL DEFAULT 'REJECT_NEW' COMMENT 'STP策略：REJECT_NEW-拒绝新订单, CANCEL_OLDEST-取消旧订单, CANCEL_NEWEST-取消新订单, CANCEL_BOTH-取消双方, DECREMENT-减量',
    `enabled` INT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用, 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_symbol_id` (`symbol_id`) COMMENT '交易品种ID唯一索引，确保每个品种只有一条配置'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自成交防范配置表，存储自成交防范策略配置';

-- ---------------------------------------------
-- 自成交防范告警表 (stp_alert)
-- 用途：记录自成交防范事件的详细信息，用于审计和监控
-- ---------------------------------------------
CREATE TABLE `stp_alert` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增长',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，触发自成交防范的用户',
    `symbol_id` INT NOT NULL COMMENT '交易品种ID，发生自成交防范的品种',
    `incoming_order_id` BIGINT NOT NULL COMMENT '新订单ID，触发STP的 incoming 订单',
    `incoming_side` INT NOT NULL COMMENT '新订单方向：0-买入(BUY), 1-卖出(SELL)',
    `incoming_price` BIGINT NOT NULL COMMENT '新订单价格',
    `incoming_quantity` BIGINT NOT NULL COMMENT '新订单数量',
    `resting_order_id` BIGINT NOT NULL COMMENT '对手订单ID，已挂在订单簿上的订单',
    `resting_side` INT NOT NULL COMMENT '对手订单方向：0-买入(BUY), 1-卖出(SELL)',
    `resting_price` BIGINT NOT NULL COMMENT '对手订单价格',
    `resting_quantity` BIGINT NOT NULL COMMENT '对手订单剩余数量',
    `overlap_quantity` BIGINT NOT NULL COMMENT '重叠数量，可能自成交的数量',
    `policy_applied` VARCHAR(32) NOT NULL COMMENT '应用的STP策略',
    `action_taken` VARCHAR(32) NOT NULL COMMENT '执行的动作：INCOMING_REJECTED, RESTING_CANCELLED, INCOMING_CANCELLED, BOTH_CANCELLED, QUANTITY_DECREMENTED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引',
    KEY `idx_symbol_id` (`symbol_id`) COMMENT '交易品种ID索引',
    KEY `idx_incoming_order_id` (`incoming_order_id`) COMMENT '新订单ID索引',
    KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自成交防范告警表，记录自成交防范事件';

-- =============================================
-- 完成提示
-- =============================================
-- 所有表结构创建完成！
-- 总计16张表：
-- 1. sys_user - 系统用户表
-- 2. trading_account - 交易账户表
-- 3. account_balance - 账户余额表
-- 4. balance_change_log - 余额变更日志表
-- 5. order_history - 订单历史表
-- 6. execution - 成交记录表
-- 7. position - 持仓表
-- 8. risk_rule - 风控规则表
-- 9. risk_alert - 风控告警表
-- 10. outbox_message - Outbox消息表
-- 11. saga_instance - Saga实例表
-- 12. saga_step_log - Saga步骤日志表
-- 13. idempotent_record - 幂等记录表
-- 14. reconcile_diff_record - 对账差异记录表
-- 15. stp_config - 自成交防范配置表
-- 16. stp_alert - 自成交防范告警表
-- =============================================
