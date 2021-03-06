package com.codeant.spring_boot.rabbitmq.delay.queue.service;

import com.codeant.spring_boot.rabbitmq.delay.queue.util.ExpirationMessagePostProcessor;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.codeant.spring_boot.rabbitmq.delay.queue.conf.QueueConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;


/**
 * 描述：接收消息
 *
 * @author LJH-1755497577 2019/10/16 11:00
 */
@Component
public class ProcessReceiver implements ChannelAwareMessageListener {
    public static CountDownLatch latch;
    private static Logger logger = LoggerFactory.getLogger(ProcessReceiver.class);

    public static final String FAIL_MESSAGE = "This message will fail";

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        try {
            processMessage(message);
        }
        catch (Exception e) {
            //过期时间 10s随机秒数
            long expiration = ((int)(Math.random()*10+1)) * 1000;

            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2) // 持久化消息
                    .contentEncoding("UTF-8")
                    .expiration(String.valueOf(expiration)) // TTL，expiration 秒后没有被消费则被发送到DLX
                    .build();


            // 如果发生了异常，则将该消息重定向到缓冲队列，会在一定延迟之后自动重做
            channel.basicPublish(QueueConfig.PER_MESSAGE_TTL_EXCHANGE_NAME,//转发到哪个exchange
                    QueueConfig.DELAY_QUEUE_PER_MESSAGE_TTL_NAME,//路由key
                    properties,//设置过期时间、是否持久化等属性
                    ("The failed message will auto retry after a certain delay" + " -- 消息过期时间：" + expiration).getBytes());
        }

        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * 模拟消息处理。如果当消息内容为FAIL_MESSAGE的话，则需要抛出异常
     *
     * @param message
     * @throws Exception
     */
    public void processMessage(Message message) throws Exception {
        String realMessage = new String(message.getBody());
        logger.info("Received <" + realMessage + ">");
        if (Objects.equals(realMessage, FAIL_MESSAGE)) {
            throw new Exception("Some exception happened");
        }
    }
}
