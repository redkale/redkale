/*
 *
 */
package org.redkale.test.cached;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.redkale.boot.LoggingBaseHandler;
import org.redkale.cached.spi.CachedManagerService;
import org.redkale.convert.json.JsonConvert;
import org.redkale.source.CacheMemorySource;
import org.redkale.util.Utility;

/** @author zhangjx */
public class CachedManagerTest {

    private static CachedManagerService manager;

    public static void main(String[] args) throws Throwable {
        LoggingBaseHandler.initDebugLogConfig();
        CachedManagerTest test = new CachedManagerTest();
        init();
        test.run1();
        test.run2();
    }

    @BeforeAll
    public static void init() throws Exception {
        CacheMemorySource remoteSource = new CacheMemorySource("cache-remote");
        remoteSource.init(null);
        manager = CachedManagerService.create(remoteSource);
        manager.init(null);
    }

    @Test
    public void run1() throws Exception {
        Duration expire = Duration.ofMillis(290);
        manager.localSetString("name", "haha", "myha", expire);
        Assertions.assertEquals(manager.localGetString("name", "haha"), "myha");
        Utility.sleep(300);
        Assertions.assertTrue(manager.localGetString("name", "haha") == null);

        CachingBean bean = new CachingBean();
        bean.setName("tom");
        bean.setRemark("这是名字备注");

        String json = bean.toString();
        manager.localSet("name", bean.getName(), CachingBean.class, bean, expire);
        Assertions.assertEquals(
                manager.localGet("name", bean.getName(), CachingBean.class).toString(), json);
        bean.setRemark(bean.getRemark() + "-新备注");
        Assertions.assertEquals(
                manager.localGet("name", bean.getName(), CachingBean.class).toString(), json);

        manager.localGetSet("group", "key1", CachingBean.class, true, 2, expire, () -> new CachingBean("v1", "r1"));
        Utility.sleep(2);
        manager.localGetSet("group", "key2", CachingBean.class, true, 2, expire, () -> new CachingBean("v2", "r2"));
        Utility.sleep(2);
        manager.localGetSet("group", "key3", CachingBean.class, true, 2, expire, () -> new CachingBean("v3", "r3"));
        Assertions.assertEquals(2, manager.getLocalSource().getSize("group"));
    }

    @Test
    public void run2() throws Exception {
        manager.setSchema("ParallelBean");
        int count = 50;
        ParallelBean bean = new ParallelBean();
        Duration localExpire = Duration.ofMillis(190);
        Duration remoteExpire = Duration.ofMillis(500);
        {
            CountDownLatch cdl = new CountDownLatch(count);
            for (int i = 0; i < count; i++) {
                new Thread(() -> {
                            manager.bothGetSet(
                                    "name",
                                    "name",
                                    String.class,
                                    false,
                                    0,
                                    localExpire,
                                    remoteExpire,
                                    () -> bean.getName());
                            cdl.countDown();
                        })
                        .start();
            }
            cdl.await();
        }
        Assertions.assertEquals(1, ParallelBean.c1.get());
        Utility.sleep(200);
        manager.bothGetSet("name", "name", String.class, false, 0, localExpire, remoteExpire, () -> bean.getName());
        Assertions.assertEquals(1, ParallelBean.c1.get());
        Utility.sleep(300);
        {
            CountDownLatch cdl = new CountDownLatch(count);
            for (int i = 0; i < count; i++) {
                new Thread(() -> {
                            manager.bothGetSet(
                                    "name",
                                    "name",
                                    String.class,
                                    false,
                                    0,
                                    localExpire,
                                    remoteExpire,
                                    () -> bean.getName());
                            cdl.countDown();
                        })
                        .start();
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

        public CachingBean() {
            //
        }

        public CachingBean(String name, String remark) {
            this.name = name;
            this.remark = remark;
        }

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
