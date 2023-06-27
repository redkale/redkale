/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.annotation;

import java.lang.annotation.*;

/**
 * 值越大，优先级越高
 * 
 * @see org.redkale.annotation.Priority
 *
 * @since Common Annotations 1.2
 *
 * @deprecated replace by org.redkale.annotation.Priority
 */
@Deprecated(since = "2.8.0")
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Priority {

    /**
     * 优先级值
     *
     * @return int
     */
    int value();
}
