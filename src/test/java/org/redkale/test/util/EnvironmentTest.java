/*
 *
 */
package org.redkale.test.util;

import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.util.Environment;

/**
 *
 * @author zhangjx
 */
public class EnvironmentTest {

    public static void main(String[] args) throws Throwable {
        EnvironmentTest test = new EnvironmentTest();
        test.run1();
    }

    @Test
    public void run1() throws Exception {
        Properties properties = new Properties();
        properties.put("age", "18");
        properties.put("haha_18", "test");
        properties.put("bb", "tt");

        Environment env = new Environment(properties);
        String val = env.getPropertyValue("school_#{name}_${haha_${age}}_${bb}_#{dd}");
        System.out.println(val);
        Assertions.assertEquals("school_#{name}_test_tt_#{dd}", val);

        val = env.getPropertyValue("${haha_${age}}");
        System.out.println(val);
        Assertions.assertEquals("test", val);
    }
}
