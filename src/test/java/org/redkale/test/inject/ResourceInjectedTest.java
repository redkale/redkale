/*
 *
 */
package org.redkale.test.inject;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.annotation.Resource;
import org.redkale.annotation.ResourceInjected;
import org.redkale.inject.ResourceFactory;
import org.redkale.service.Service;

/** @author zhangjx */
public class ResourceInjectedTest {

    public static void main(String[] args) throws Throwable {
        ResourceInjectedTest test = new ResourceInjectedTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        ResourceFactory factory = ResourceFactory.create();
        factory.register("res.id", "2345");
        factory.register("res.name", "my old name");
        ResourceService res = new ResourceService();
        factory.inject(res);
        factory.register("", res);
        RoomService serice = new RoomService();
        factory.inject(serice);
        Assertions.assertEquals(1, ResourceService.counter.get());
    }

    public static class RoomService implements Service {

        @Resource
        private ResourceService resService;

        public void test() {
            resService.doing();
        }
    }

    public static class ResourceService implements Service {

        private static final AtomicInteger counter = new AtomicInteger();

        @Resource(name = "res.id")
        private int id;

        @Resource(name = "res.name")
        private String name;

        @ResourceInjected
        private void onInjected(Object src, String fieldName) {
            counter.incrementAndGet();
            System.out.println("资源被注入到对象(" + src + ")的字段(" + fieldName + ")上");
        }

        public void doing() {
            System.out.println("id = " + id + ", name = " + name);
        }
    }
}
