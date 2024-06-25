/*
 *
 */
package org.redkale.test.scheduled;

import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.scheduled.Scheduled;
import org.redkale.service.Service;
import org.redkale.util.Times;

/** @author zhangjx */
public class ScheduledTestService implements Service {
    public AtomicInteger count1 = new AtomicInteger();
    public AtomicInteger count2 = new AtomicInteger();
    public AtomicInteger count3 = new AtomicInteger();

    @Scheduled(cron = "0/1 * * * * ?")
    public void task1() {
        count1.incrementAndGet();
        System.out.println(Times.nowMillis() + "每秒-----------执行task1");
    }

    @Scheduled(name = "task2", cron = "0/1 * * * * ?")
    public String task2() {
        count2.incrementAndGet();
        System.out.println(Times.nowMillis() + "每秒*****执行task2");
        return "";
    }

    @Scheduled(cron = "0/1 * * * * ?")
    private void task3() {
        count3.incrementAndGet();
        System.out.println(Times.nowMillis() + "每秒执行task3");
    }
}
