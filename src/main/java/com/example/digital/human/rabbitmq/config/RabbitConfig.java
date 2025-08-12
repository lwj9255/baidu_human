package com.example.digital.human.rabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    //配置一个Direct类型的交换
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange("bootDirectExchange", true, false);
    }

    //配置一个队列
    @Bean
    public Queue directQueue() {
        return new Queue("bootDirectQueue", true, false, false);
    }

    /**
     * 配置一个队列和交换机的绑定
     *
     * @param directQueue    需要绑定的队列的对象，参数名必须要与某个@Bean的方法名完全相同这样就会自动进行注入
     * @param directExchange 需要绑定的交换机的对象，参数名必须要与某个@Bean的方法名完全相同这样就会自动进行注入
     * @return
     */
    @Bean
    public Binding directBinding(Queue directQueue, DirectExchange directExchange) {
        //完成绑定
        // 参数 1 为需要绑定的队列
        // 参数 2 为需要绑定的交换机
        // 参数 3绑定时的RoutingKey
        return BindingBuilder.bind(directQueue).to(directExchange).with("bootDirectRoutingKey");
    }
}
