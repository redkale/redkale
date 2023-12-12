/*
 *
 */
package org.redkale.test.cache;

import java.time.Duration;
import org.junit.jupiter.api.*;
import org.redkale.cache.support.CacheManagerService;
import org.redkale.convert.json.JsonConvert;
import org.redkale.source.CacheMemorySource;
import org.redkale.util.Utility;
/**
 *
 * @author zhangjx
 */
public class CachingTest {

    private static CacheManagerService manager;

    public static void main(String[] args) throws Throwable {
        CachingTest test = new CachingTest();
        test.wait();
        test.run();
    }

    @BeforeAll
    public static void init() throws Exception {
        CacheMemorySource remoteSource = new CacheMemorySource("remote");
        remoteSource.init(null);
        manager = CacheManagerService.create(remoteSource);
        manager.init(null);
    }

    @Test
    public void run() throws Exception {
        Duration expire = Duration.ofMillis(490);
        manager.localSetString("user", "name:haha", "myha", expire);
        Assertions.assertEquals(manager.localGetString("user", "name:haha"), "myha");
        Utility.sleep(500);
        Assertions.assertTrue(manager.localGetString("user", "name:haha") == null);

        CachingBean bean = new CachingBean();
        bean.setName("tom");
        bean.setRemark("这是名字备注");

        String json = bean.toString();
        manager.localSet("user", bean.getName(), CachingBean.class, bean, expire);
        Assertions.assertEquals(manager.localGet("user", bean.getName(), CachingBean.class).toString(), json);
        bean.setRemark(bean.getRemark() + "-新备注");
        Assertions.assertEquals(manager.localGet("user", bean.getName(), CachingBean.class).toString(), json);
        manager.destroy(null);
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
