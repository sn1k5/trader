package com.cpptrader.admin.consistency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 可选配置
 * 通过设置 spring.rabbitmq.enabled=false 可以禁用 RabbitMQ
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQOptionalConfig {

    /**
     * 检查 RabbitMQ 连接是否可用
     */
    @Bean
    public boolean rabbitMQConnectionChecker(ConnectionFactory connectionFactory) {
        try {
            connectionFactory.createConnection().close();
            log.info("RabbitMQ connection successful");
            return true;
        } catch (Exception e) {
            log.warn("RabbitMQ is not available: {}. Some features may be disabled.", e.getMessage());
            return false;
        }
    }
}
