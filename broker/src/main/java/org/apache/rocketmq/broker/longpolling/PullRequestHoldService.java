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
package org.apache.rocketmq.broker.longpolling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.common.SystemClock;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.store.ConsumeQueueExt;

/**
 * Pull请求 Hold服务
 */
public class PullRequestHoldService extends ServiceThread {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);

    /**
     * topic队列分隔符
     */
    private static final String TOPIC_QUEUEID_SEPARATOR = "@";
    private final BrokerController brokerController;
    private final SystemClock systemClock = new SystemClock();

    /**
     * 拉请求HashMap
     */
    private ConcurrentMap<String/* topic@queueId */, ManyPullRequest> pullRequestTable =
        new ConcurrentHashMap<String, ManyPullRequest>(1024);

    public PullRequestHoldService(final BrokerController brokerController) {
        this.brokerController = brokerController;
    }

    /**
     * 暂停pull请求
     *
     * @param topic
     * @param queueId
     * @param pullRequest
     */
    public void suspendPullRequest(final String topic, final int queueId, final PullRequest pullRequest) {
        /**
         * 构造key
         */
        String key = this.buildKey(topic, queueId);
        /**
         * 获取请求
         */
        ManyPullRequest mpr = this.pullRequestTable.get(key);
        if (null == mpr) {
            /**
             * 如果为空，新创建
             *
             * 如果此时table中没有，返回null
             * 如果有，返回旧值
             */
            mpr = new ManyPullRequest();
            ManyPullRequest prev = this.pullRequestTable.putIfAbsent(key, mpr);
            if (prev != null) {
                /**
                 * 存在的时候，有，赋旧值
                 */
                mpr = prev;
            }
        }

        mpr.addPullRequest(pullRequest);
    }

    private String buildKey(final String topic, final int queueId) {
        StringBuilder sb = new StringBuilder();
        sb.append(topic);
        sb.append(TOPIC_QUEUEID_SEPARATOR);
        sb.append(queueId);
        return sb.toString();
    }

    @Override
    public void run() {
        log.info("{} service started", this.getServiceName());
        while (!this.isStopped()) {
            try {
                /**
                 * 如果Broker开启了长轮询，等待5秒钟
                 */
                if (this.brokerController.getBrokerConfig().isLongPollingEnable()) {
                    this.waitForRunning(5 * 1000);
                } else {
                    /**
                     * 否则等待短轮询 。不设置时，默认1秒
                     */
                    this.waitForRunning(this.brokerController.getBrokerConfig().getShortPollingTimeMills());
                }

                long beginLockTimestamp = this.systemClock.now();

                /**
                 * 校验hold请求
                 */
                this.checkHoldRequest();

                long costTime = this.systemClock.now() - beginLockTimestamp;
                if (costTime > 5 * 1000) {
                    log.info("[NOTIFYME] check hold request cost {} ms.", costTime);
                }
            } catch (Throwable e) {
                log.warn(this.getServiceName() + " service has exception. ", e);
            }
        }

        log.info("{} service end", this.getServiceName());
    }

    @Override
    public String getServiceName() {
        return PullRequestHoldService.class.getSimpleName();
    }

    /**
     * 校验持有请求
     */
    private void checkHoldRequest() {
        for (String key : this.pullRequestTable.keySet()) {
            String[] kArray = key.split(TOPIC_QUEUEID_SEPARATOR);
            if (2 == kArray.length) {
                /**
                 * 获取topic
                 */
                String topic = kArray[0];
                /**
                 * 获取queueId
                 */
                int queueId = Integer.parseInt(kArray[1]);

                /**
                 * 获取最大offset
                 */
                final long offset = this.brokerController.getMessageStore().getMaxOffsetInQueue(topic, queueId);
                try {
                    /**
                     * notify 消息到达
                     */
                    this.notifyMessageArriving(topic, queueId, offset);
                } catch (Throwable e) {
                    log.error("check hold request failed. topic={}, queueId={}", topic, queueId, e);
                }
            }
        }
    }

    /**
     * notify消息到达
     *
     * @param topic
     * @param queueId
     * @param maxOffset
     */
    public void notifyMessageArriving(final String topic, final int queueId, final long maxOffset) {
        notifyMessageArriving(topic, queueId, maxOffset, null, 0, null, null);
    }

    /**
     * 消息到达
     *
     * @param topic
     * @param queueId
     * @param maxOffset
     * @param tagsCode
     * @param msgStoreTime
     * @param filterBitMap
     * @param properties
     */
    public void notifyMessageArriving(final String topic, final int queueId, final long maxOffset, final Long tagsCode,
        long msgStoreTime, byte[] filterBitMap, Map<String, String> properties) {
        String key = this.buildKey(topic, queueId);

        /**
         * pull请求HashMap
         */
        ManyPullRequest mpr = this.pullRequestTable.get(key);
        if (mpr != null) {
            /**
             * 拷贝过来
             */
            List<PullRequest> requestList = mpr.cloneListAndClear();
            if (requestList != null) {
                List<PullRequest> replayList = new ArrayList<PullRequest>();

                /**
                 * for循环上述pull请求
                 */
                for (PullRequest request : requestList) {
                    long newestOffset = maxOffset;

                    /**
                     * 如果offset小于请求计划的offset
                     *
                     * 说明需要更新最新的offset
                     */
                    if (newestOffset <= request.getPullFromThisOffset()) {
                        newestOffset = this.brokerController.getMessageStore().getMaxOffsetInQueue(topic, queueId);
                    }

                    /**
                     * 如果offset大于请求计划的offset
                     *
                     */
                    if (newestOffset > request.getPullFromThisOffset()) {
                        /**
                         * 判断是否匹配
                         */
                        boolean match = request.getMessageFilter().isMatchedByConsumeQueue(tagsCode,
                            new ConsumeQueueExt.CqExtUnit(tagsCode, msgStoreTime, filterBitMap));
                        // match by bit map, need eval again when properties is not null.
                        if (match && properties != null) {
                            match = request.getMessageFilter().isMatchedByCommitLog(null, properties);
                        }

                        if (match) {
                            try {
                                /**
                                 * 如果匹配，执行请求当wakeup时
                                 */
                                this.brokerController.getPullMessageProcessor().executeRequestWhenWakeup(request.getClientChannel(),
                                    request.getRequestCommand());
                            } catch (Throwable e) {
                                log.error("execute request when wakeup failed.", e);
                            }
                            continue;
                        }
                    }

                    if (System.currentTimeMillis() >= (request.getSuspendTimestamp() + request.getTimeoutMillis())) {
                        try {
                            this.brokerController.getPullMessageProcessor().executeRequestWhenWakeup(request.getClientChannel(),
                                request.getRequestCommand());
                        } catch (Throwable e) {
                            log.error("execute request when wakeup failed.", e);
                        }
                        continue;
                    }

                    replayList.add(request);
                }

                if (!replayList.isEmpty()) {
                    mpr.addPullRequest(replayList);
                }
            }
        }
    }
}
