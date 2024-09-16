/*
 *
 */
package org.redkale.test.scheduled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.scheduled.spi.ScheduleManagerService;
import org.redkale.util.Utility;

/** @author zhangjx */
public class ScheduledTest {

    public static void main(String[] args) throws Throwable {
        ScheduledTest test = new ScheduledTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        ScheduleManagerService manager = ScheduleManagerService.create(null);
        manager.init(null);
        ScheduledTestService service = new ScheduledTestService();
        manager.schedule(service);
        System.out.println("开始执行");
        Utility.sleep(2000);
        manager.stop("task2");
        Utility.sleep(1001);
        manager.unschedule(service);
        manager.destroy(null);
        Assertions.assertEquals(3, service.count1.get());
        Assertions.assertEquals(2, service.count2.get());
        Assertions.assertEquals(3, service.count3.get());
    }
}
