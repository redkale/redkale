/*
 *
 */
package org.redkale.test.scheduling;

import org.junit.jupiter.api.Test;
import org.redkale.scheduling.ScheduledFactory;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class ScheduleTest {

    public static void main(String[] args) throws Throwable {
        ScheduleTest test = new ScheduleTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        ScheduledFactory factory = ScheduledFactory.create(null);
        ScheduleService service = new ScheduleService();
        factory.schedule(service);
        Utility.sleep(3000);
        factory.unschedule(service);
        factory.destroy();
    }

}
