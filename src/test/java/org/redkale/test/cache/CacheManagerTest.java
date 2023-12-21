/*
 *
 */
package org.redkale.test.cache;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.redkale.cache.spi.CacheManagerService;
import org.redkale.convert.json.JsonConvert;
import org.redkale.source.CacheMemorySource;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class CacheManagerTest {

    private static CacheManagerService manager;

    public static void main(String[] args) throws Throwable {
        CacheManagerTest test = new CacheManagerTest();
        init();
        test.run1();
        test.run2();
    }

    @BeforeAll
    public static void init() throws Exception {
        CacheMemorySource remoteSource = new CacheMemorySource("cache-remote");
        remoteSource.init(null);
        manager = CacheManagerService.create(remoteSource);
        manager.init(null);
    }

    @Test
    public void run1() throws Exception {
        Duration expire = Duration.ofMillis(290);
        manager.localSetString("user", "name:haha", "myha", expire);
        Assertions.assertEquals(manager.localGetString("user", "name:haha"), "myha");
        Utility.sleep(300);
        Assertions.assertTrue(manager.localGetString("user", "name:haha") == null);

        CachingBean bean = new CachingBean();
        bean.setName("tom");
        bean.setRemark("这是名字备注");

        String json = bean.toString();
        manager.localSet("user", bean.getName(), CachingBean.class, bean, expire);
        Assertions.assertEquals(manager.localGet("user", bean.getName(), CachingBean.class).toString(), json);
        bean.setRemark(bean.getRemark() + "-新备注");
        Assertions.assertEquals(manager.localGet("user", bean.getName(), CachingBean.class).toString(), json);
    }

    @Test
    public void run2() throws Exception {
        int count = 50;
        ParallelBean bean = new ParallelBean();
        Duration localExpire = Duration.ofMillis(190);
        Duration remoteExpire = Duration.ofMillis(400);
        {
            CountDownLatch cdl = new CountDownLatch(count);
            for (int i = 0; i < count; i++) {
                new Thread(() -> {
                    manager.bothGetSet("ParallelBean", "name", String.class, false, localExpire, remoteExpire, () -> bean.getName());
                    cdl.countDown();
                }).start();
            }
            cdl.await();
        }
        Assertions.assertEquals(1, ParallelBean.c1.get());
        Utility.sleep(200);
        manager.bothGetSet("ParallelBean", "name", String.class, false, localExpire, remoteExpire, () -> bean.getName());
        Assertions.assertEquals(1, ParallelBean.c1.get());
        Utility.sleep(200);
        {
            CountDownLatch cdl = new CountDownLatch(count);
            for (int i = 0; i < count; i++) {
                new Thread(() -> {
                    manager.bothGetSet("ParallelBean", "name", String.class, false, localExpire, remoteExpire, () -> bean.getName());
                    cdl.countDown();
                }).start();
            }
            cdl.await();
        }
        Assertions.assertEquals(2, ParallelBean.c1.get());

    }

    public static class ParallelBean {

        public static final AtomicInteger c1 = new AtomicInteger();

        public String getName() {
            c1.incrementAndGet();
            System.out.println("执行了getName方法(" + c1.get() + ")");
            return "hello";
        }
    }

    public static class CachingBean {

        private String name;

        private String remark;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
