/*
 */
package org.redkale.test.util;

import org.junit.jupiter.api.*;
import org.redkale.annotation.Resource;
import org.redkale.convert.json.JsonFactory;
import org.redkale.inject.ResourceFactory;

/**
 *
 * @author zhangjx
 */
public class ResourceLoaderTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        ResourceLoaderTest test = new ResourceLoaderTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        ResourceFactory factory = ResourceFactory.create();
        factory.register("a.id", 1234);
        factory.register("a.name", "my a name");
        factory.register("b.id", 4321);
        factory.register("b.name", "my b name");
        Bean bean = new Bean();
        factory.register("a", bean);
        factory.inject("a", bean);

        ParentBean pb = new ParentBean();
        factory.inject(pb);
        if (!main) Assertions.assertEquals(new Bean(1234, "my a name").toString(), pb.bean.toString());
        System.out.println(pb.bean);
    }

    public static class ParentBean {

        @Resource(name = "a")
        public Bean bean;
    }

    public static class Bean {

        @Resource(name = "@.id")
        public int id;

        @Resource(name = "@.name")
        public String name;

        public Bean() {
        }

        public Bean(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }
    }
}
