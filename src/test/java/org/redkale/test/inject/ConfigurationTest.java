/*

*/
package org.redkale.test.inject;

import org.junit.jupiter.api.*;
import org.redkale.annotation.Resource;
import org.redkale.convert.json.JsonFactory;
import org.redkale.inject.Configuration;
import org.redkale.inject.ResourceFactory;

/**
 *
 * @author zhangjx
 */
public class ConfigurationTest {

    public static void main(String[] args) throws Throwable {
        ConfigurationTest test = new ConfigurationTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        ResourceFactory factory = ResourceFactory.create();
        factory.register("a.id", 1234);
        factory.register("a.name", "my a name");
        factory.register("b.id", 4321);
        factory.register("b.name", "my b name");
        factory.register(DiyConfiguration.class);

        BeanB pb = new BeanB();
        factory.inject(pb);
        Assertions.assertEquals(new BeanA(1234, "my a name", "auto").toString(), pb.bean.toString());
        System.out.println(pb.bean);
    }

    public static class DiyConfiguration implements Configuration {

        @Resource(name = "a")
        BeanA createBeanA() {
            System.out.println("创建一个Bean");
            BeanA bean = new BeanA();
            bean.desc = "auto";
            return bean;
        }
    }

    public static class BeanB {

        @Resource(name = "a")
        public BeanA bean;
    }

    public static class BeanA {

        @Resource(name = "@.id")
        public int id;

        @Resource(name = "@.name")
        public String name;

        public String desc;

        public BeanA() {}

        public BeanA(int id, String name, String desc) {
            this.id = id;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }
    }
}
