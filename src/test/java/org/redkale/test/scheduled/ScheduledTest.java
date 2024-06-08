/*
 *
 */
package org.redkale.test.scheduled;

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
        ScheduledService service = new ScheduledService();
        manager.schedule(service);
        Utility.sleep(3000);
        manager.unschedule(service);
        manager.destroy(null);
    }
}
