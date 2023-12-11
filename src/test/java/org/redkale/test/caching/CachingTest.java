/*
 *
 */
package org.redkale.test.caching;

import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.caching.CacheConfig;
import org.redkale.caching.CacheManager;
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
        cache.localSetString("user", "name:haha", "haha", Duration.ofMillis(500));
        Utility.sleep(501);
        Assertions.assertTrue(cache.localGetString("user", "name:haha") == null);
    }
}
