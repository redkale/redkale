/*
 *
 */
package org.redkale.test.schedule;

import org.redkale.service.Service;
import org.redkale.util.Times;
import org.redkale.schedule.Scheduled;

/**
 *
 * @author zhangjx
 */
public class ScheduleService implements Service {

    @Scheduled(cron = "0/1 * * * * ?")
    public void task1() {
        System.out.println(Times.nowMillis() + "每秒-----------执行task1");
    }

    @Scheduled(cron = "0/1 * * * * ?")
    public String task2() {
        System.out.println(Times.nowMillis() + "每秒*****执行task2");
        return "";
    }

    @Scheduled(cron = "0/1 * * * * ?")
    private void task3() {
        System.out.println(Times.nowMillis() + "每秒执行task3");
    }
}
