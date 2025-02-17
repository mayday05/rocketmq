/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.example.simple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.common.RemotingHelper;

/**
 * 增加密钥的生产、消费
 */
public class AclClient {

    private static final Map<MessageQueue, Long> OFFSE_TABLE = new HashMap<MessageQueue, Long>();

    /**
     * 密钥
     */
    private static final String ACL_ACCESS_KEY = "RocketMQ";

    /**
     * 密钥
     */
    private static final String ACL_SECRET_KEY = "1234567";

    public static void main(String[] args) throws MQClientException, InterruptedException {
        // 分别启动生产者、PUSH消费者、PULL消费者
        producer();
        pushConsumer();
        pullConsumer();
    }

    /**
     * 启动生产者
     *
     * @throws MQClientException
     */
    public static void producer() throws MQClientException {
        /**
         * 构造生产者，传入钩子函数
         */
        DefaultMQProducer producer = new DefaultMQProducer("ProducerGroupName", getAclRPCHook());
        /**
         * 设置NameSrv地址
         */
        producer.setNamesrvAddr("192.168.180.11:39876");
        producer.start();

        for (int i = 0; i < 128; i++) {
            try {
                {
                    Message msg = new Message("TopicTest",
                            "TagA",
                            "OrderID188",
                            "Hello world".getBytes(RemotingHelper.DEFAULT_CHARSET));
                    SendResult sendResult = producer.send(msg);
                    System.out.printf("%s%n", sendResult);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        producer.shutdown();
    }

    /**
     * Push类型消费者
     *
     * @throws MQClientException
     */
    public static void pushConsumer() throws MQClientException {

        /**
         * 初始化Push类型消费者，传入指定的消费者名称、RPC钩子函数、分配策略
         */
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("please_rename_unique_group_name_5",
                getAclRPCHook(), new AllocateMessageQueueAveragely());
        consumer.setNamesrvAddr("192.168.180.11:39876");
        consumer.subscribe("TopicTest", "*");
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // Wrong time format 2017_0422_221800
        consumer.setConsumeTimestamp("20191214154000");

        // 注册消息监听器
        consumer.registerMessageListener(new MessageListenerConcurrently() {

            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
                printBody(msgs);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        // 启动消费者
        consumer.start();
        System.out.printf("Consumer Started.%n");
    }

    /**
     * Pull类型消费者
     *
     * @throws MQClientException
     */
    public static void pullConsumer() throws MQClientException {
        /**
         * 初始化pull类型消费者
         */
        DefaultMQPullConsumer consumer = new DefaultMQPullConsumer("please_rename_unique_group_name_6",
                getAclRPCHook());
        consumer.setNamesrvAddr("192.168.180.11:39876");
        consumer.start();

        /**
         * 拉起订阅队列
         */
        Set<MessageQueue> mqs = consumer.fetchSubscribeMessageQueues("TopicTest");
        for (MessageQueue mq : mqs) {
            System.out.printf("Consume from the queue: %s%n", mq);
            // label用于控制多层循环的退出或者continue
            SINGLE_MQ:
            while (true) {
                try {
                    /**
                     * 需要消费端维护OffSet
                     */
                    PullResult pullResult =
                            consumer.pullBlockIfNotFound(mq, null, getMessageQueueOffset(mq), 32);
                    System.out.printf("%s%n", pullResult);

                    // 保存下一个offSet
                    putMessageQueueOffset(mq, pullResult.getNextBeginOffset());

                    // 打印消息体
                    printBody(pullResult);
                    // 还需要处理拉取结果
                    switch (pullResult.getPullStatus()) {
                        case FOUND:
                            break;
                        case NO_MATCHED_MSG:
                            break;
                        case NO_NEW_MSG:
                            break SINGLE_MQ;
                        case OFFSET_ILLEGAL:
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        consumer.shutdown();
    }

    private static void printBody(PullResult pullResult) {
        printBody(pullResult.getMsgFoundList());
    }

    private static void printBody(List<MessageExt> msg) {
        if (msg == null || msg.size() == 0)
            return;
        for (MessageExt m : msg) {
            if (m != null) {
                System.out.printf("msgId : %s  body : %s  \n\r", m.getMsgId(), new String(m.getBody()));
            }
        }
    }

    /**
     * 获取消息队列当前的offset
     *
     * @param mq
     * @return
     */
    private static long getMessageQueueOffset(MessageQueue mq) {
        Long offset = OFFSE_TABLE.get(mq);
        if (offset != null) {
            return offset;
        }
        return 0;
    }

    private static void putMessageQueueOffset(MessageQueue mq, long offset) {
        OFFSE_TABLE.put(mq, offset);
    }

    static RPCHook getAclRPCHook() {
        return new AclClientRPCHook(new SessionCredentials(ACL_ACCESS_KEY, ACL_SECRET_KEY));
    }
}
