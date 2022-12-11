/*
 */
package org.redkale.test.util;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;
import org.junit.jupiter.api.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class ResourceListenerTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        ResourceListenerTest test = new ResourceListenerTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        AtomicInteger aCounter = new AtomicInteger();
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

        if (!main) {
            Assertions.assertTrue(aservice.counter.get() == 1);
            Assertions.assertTrue(bservice.counter.get() == 2);
            Assertions.assertTrue(abservice.counter.get() == 2);
        }
                
        factory.register("property.id", "7777");
        
        if (!main) {
            Assertions.assertTrue(aservice.counter.get() == 2);
            Assertions.assertTrue(bservice.counter.get() == 2);
            Assertions.assertTrue(abservice.counter.get() == 3);
        }
    }

    class AService {

        public final AtomicInteger counter = new AtomicInteger();

        @Resource(name = "property.id", required = false)
        private String id;

        @Resource(name = "property.desc", required = false)
        private String desc;

        @ResourceListener
        private void changeResource(ResourceEvent[] events) {
            for (ResourceEvent event : events) {
                counter.incrementAndGet();
                System.out.println(getClass().getSimpleName() + " @Resource = " + event.name() + " 资源变更:  newVal = " + event.newValue() + ", oldVal = " + event.oldValue());
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

        @ResourceListener
        private void changeResource(ResourceEvent[] events) {
            for (ResourceEvent event : events) {
                counter.incrementAndGet();
                System.out.println(getClass().getSimpleName() + " @Resource = " + event.name() + " 资源变更:  newVal = " + event.newValue() + ", oldVal = " + event.oldValue());
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

        @ResourceListener
        private void changeResource(ResourceEvent[] events) {
            for (ResourceEvent event : events) {
                counter.incrementAndGet();
                System.out.println(getClass().getSimpleName() + " @Resource = " + event.name() + " 资源变更:  newVal = " + event.newValue() + ", oldVal = " + event.oldValue());
            }
            System.out.println(getClass().getSimpleName() + " env = " + env);
        }

        public String test() {
            return "";
        }
    }
}
