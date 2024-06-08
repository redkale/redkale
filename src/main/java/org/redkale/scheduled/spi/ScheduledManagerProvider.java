/*
 *
 */
package org.redkale.scheduled.spi;

import org.redkale.scheduled.ScheduledManager;
import org.redkale.util.InstanceProvider;

/**
 * 自定义的ScheduledManager加载器, 如果标记&#64;Priority加载器的优先级需要大于1000， 1000以下预留给官方加载器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface ScheduledManagerProvider extends InstanceProvider<ScheduledManager> {}
