/*
 *
 */
package org.redkale.scheduled;

import java.util.List;

/**
 * 定时管理器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface ScheduledManager {

    /**
     * 开启宿主对象中所有的定时任务方法. 存在定时任务方法返回true，否则返回false
     *
     * @param service 宿主对象
     */
    public void schedule(Object service);

    /**
     * 开启所有宿主对象中指定的任务名
     *
     * @see org.redkale.scheduled.Scheduled#name()
     * @param scheduleName 定时任务名称
     * @return 返回任务数量
     */
    public int start(String scheduleName);

    /**
     * 执行所有宿主对象中指定的任务名
     *
     * @see org.redkale.scheduled.Scheduled#name()
     * @param scheduleName 定时任务名称
     * @param all 是执行所有同名任务还是只执行其中任意一个
     * @return 结果集合
     */
    public List<Object> execute(String scheduleName, boolean all);

    /**
     * 关闭宿主对象中所有的定时任务方法
     *
     * @param service 宿主对象
     */
    public void unschedule(Object service);

    /**
     * 关闭所有宿主对象中指定的任务名
     *
     * @see org.redkale.scheduled.Scheduled#name()
     * @param scheduleName 定时任务名称
     * @return 返回任务数量
     */
    public int stop(String scheduleName);
}
