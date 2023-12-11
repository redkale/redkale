/*
 *
 */
package org.redkale.test.caching;

import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.caching.CacheConfig;
import org.redkale.caching.CacheManager;
import org.redkale.convert.json.JsonConvert;
import org.redkale.source.CacheMemorySource;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class CachingTest {

    public static void main(String[] args) throws Throwable {
        CachingTest test = new CachingTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        CacheManager cache = CacheManager.create(new CacheConfig(), new CacheMemorySource("remote"));
        Duration expire = Duration.ofMillis(490);
        cache.localSetString("user", "name:haha", "myha", expire);
        Assertions.assertEquals(cache.localGetString("user", "name:haha"), "myha");
        Utility.sleep(500);
        Assertions.assertTrue(cache.localGetString("user", "name:haha") == null);

        CachingBean bean = new CachingBean();
        bean.setName("tom");
        bean.setRemark("这是名字备注");

        String json = bean.toString();
        cache.localSet("user", bean.getName(), CachingBean.class, bean, expire);
        Assertions.assertEquals(cache.localGet("user", bean.getName(), CachingBean.class).toString(), json);
        bean.setRemark(bean.getRemark() + "-新备注");
        Assertions.assertEquals(cache.localGet("user", bean.getName(), CachingBean.class).toString(), json);
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
