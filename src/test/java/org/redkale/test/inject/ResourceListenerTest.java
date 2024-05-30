/*
 */
package org.redkale.test.inject;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.redkale.annotation.Resource;
import org.redkale.annotation.ResourceChanged;
import org.redkale.inject.ResourceEvent;
import org.redkale.inject.ResourceFactory;
import org.redkale.util.*;

/** @author zhangjx */
public class ResourceListenerTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        ResourceListenerTest test = new ResourceListenerTest();
        test.main = true;
        test.run1();
        test.run2();
    }

    @Test
    public void run1() throws Exception {
        Properties env = new Properties();
        env.put("property.id", "2345");
        ResourceFactory factory = ResourceFactory.create();
        factory.register(new Environment(env));

        AService aservice = new AService();
        BService bservice = new BService();
        ABService abservice = new ABService();

        factory.inject(aservice);
        factory.inject(bservice);
        factory.inject(abservice);

        Properties prop = new Properties();
        prop.put("property.id", "7890");
        prop.put("property.name", "my name");
        factory.register(prop, "", Environment.class);

        Assertions.assertEquals("7890", aservice.id);
        Assertions.assertTrue(aservice.counter.get() == 1);
        Assertions.assertTrue(bservice.counter.get() == 2);
        Assertions.assertTrue(abservice.counter.get() == 2);

        factory.register("property.id", "7777");

        Assertions.assertTrue(aservice.counter.get() == 2);
        Assertions.assertTrue(bservice.counter.get() == 2);
        Assertions.assertTrue(abservice.counter.get() == 3);
    }

    @Test
    public void run2() throws Exception {
        Properties env = new Properties();
        ResourceFactory factory = ResourceFactory.create();
        factory.register(new Environment(env));
        AService aservice = new AService();
        factory.inject(aservice);
        Assertions.assertEquals("33", aservice.id);
        factory.register("property.id", "7777");
        Assertions.assertEquals("7777", aservice.id);
    }

    class AService {

        public final AtomicInteger counter = new AtomicInteger();

        @Resource(name = "${property.id:33}", required = false)
        private String id;

        @Resource(name = "property.desc", required = false)
        private String desc;

        @ResourceChanged
        private void changeResource(ResourceEvent[] events) {
            for (ResourceEvent event : events) {
                counter.incrementAndGet();
                System.out.println(getClass().getSimpleName() + " @Resource = " + event.name() + " 资源变更:  newVal = "
                        + event.newValue() + ", oldVal = " + event.oldValue());
            }
        }

        public String test() {
            return "";
        }
    }

    class BService {

        public final AtomicInteger counter = new AtomicInteger();

        @Resource
        private Environment env;

        @ResourceChanged
        private void changeResource(ResourceEvent[] events) {
            for (ResourceEvent event : events) {
                counter.incrementAndGet();
                System.out.println(getClass().getSimpleName() + " @Resource = " + event.name() + " 资源变更:  newVal = "
                        + event.newValue() + ", oldVal = " + event.oldValue());
            }
            System.out.println(getClass().getSimpleName() + " env = " + env);
        }

        public String test() {
            return "";
        }
    }

    class ABService {

        public final AtomicInteger counter = new AtomicInteger();

        @Resource(name = "property.id", required = false)
        private String id;

        @Resource
        private Environment env;

        @ResourceChanged
        private void changeResource(ResourceEvent[] events) {
            for (ResourceEvent event : events) {
                counter.incrementAndGet();
                System.out.println(getClass().getSimpleName() + " @Resource = " + event.name() + " 资源变更:  newVal = "
                        + event.newValue() + ", oldVal = " + event.oldValue());
            }
            System.out.println(getClass().getSimpleName() + " env = " + env);
        }

        public String test() {
            return "";
        }
    }
}
