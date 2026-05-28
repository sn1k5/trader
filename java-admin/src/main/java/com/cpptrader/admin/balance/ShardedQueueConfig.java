package com.cpptrader.admin.balance;

import com.cpptrader.admin.consistency.RabbitMQConfig;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ShardedQueueConfig {

    public static final String SHARD_EXCHANGE = "cpptrader.balance.shard";
    public static final String SHARD_QUEUE_PREFIX = "balance.deduct.shard.";

    @Value("${balance.shard.count:16}")
    private int shardCount;

    public int getShardCount() {
        return shardCount;
    }

    @Bean
    public DirectExchange shardExchange() {
        return new DirectExchange(SHARD_EXCHANGE, true, false);
    }

    @Bean
    public List<Queue> shardQueues() {
        List<Queue> queues = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            queues.add(QueueBuilder.durable(SHARD_QUEUE_PREFIX + i)
                    .withArgument("x-dead-letter-exchange", RabbitMQConfig.DEAD_LETTER_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", RabbitMQConfig.DEAD_LETTER_ROUTING_KEY)
                    .build());
        }
        return queues;
    }

    @Bean
    public List<Binding> shardBindings(DirectExchange shardExchange, List<Queue> shardQueues) {
        List<Binding> bindings = new ArrayList<>();
        for (int i = 0; i < shardQueues.size(); i++) {
            bindings.add(BindingBuilder.bind(shardQueues.get(i)).to(shardExchange).with(String.valueOf(i)));
        }
        return bindings;
    }
}
