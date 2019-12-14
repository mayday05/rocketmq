/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.example.filter;

import org.apache.rocketmq.common.filter.FilterContext;
import org.apache.rocketmq.common.filter.MessageFilter;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 自定义消息过滤函数
 */
public class MessageFilterImpl implements MessageFilter {

    /**
     * 过滤规则，如果消息属性中有SequenceId属性，且数值大于100的10的倍数
     *
     * @param msg
     * @param context
     * @return
     */
    @Override
    public boolean match(MessageExt msg, FilterContext context) {
        String property = msg.getProperty("SequenceId");
        if (property != null) {
            int id = Integer.parseInt(property);
            if (((id % 10) == 0) &&
                    (id > 100)) {
                return true;
            }
        }

        return false;
    }
}
