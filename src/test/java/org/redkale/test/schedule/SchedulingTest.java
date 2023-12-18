/*
 *
 */
package org.redkale.test.schedule;

import org.junit.jupiter.api.Test;
import org.redkale.schedule.DefaultScheduleManager;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class SchedulingTest {

    public static void main(String[] args) throws Throwable {
        SchedulingTest test = new SchedulingTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        DefaultScheduleManager manager = DefaultScheduleManager.create(null);
        manager.init(null);
        ScheduleService service = new ScheduleService();
        manager.schedule(service);
        Utility.sleep(3000);
        manager.unschedule(service);
        manager.destroy(null);
    }

}
