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
package org.apache.rocketmq.client.impl.producer;

import java.util.Set;
import org.apache.rocketmq.client.producer.TransactionCheckListener;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.header.CheckTransactionStateRequestHeader;

/**
 * MQ 生产者接口
 */
public interface MQProducerInner {

    /**
     * 获取所有topic 列表
     *
     * @return
     */
    Set<String> getPublishTopicList();

    /**
     * topic 是否需要更新
     *
     * @param topic
     * @return
     */
    boolean isPublishTopicNeedUpdate(final String topic);

    TransactionCheckListener checkListener();


    /**
     * 获取校验 监听
     * @return
     */
    TransactionListener getCheckListener();

    /**
     * 校验事务状态
     *
     * @param addr
     * @param msg
     * @param checkRequestHeader
     */
    void checkTransactionState(
        final String addr,
        final MessageExt msg,
        final CheckTransactionStateRequestHeader checkRequestHeader);

    /**
     * 更新Topic 发布信息
     *
     * @param topic
     * @param info
     */
    void updateTopicPublishInfo(final String topic, final TopicPublishInfo info);

    /**
     * 是否是Unit模式
     * @return
     */
    boolean isUnitMode();
}
