/*

*/

package org.redkale.test.inject;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;
import org.redkale.annotation.Resource;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.source.DataMemorySource;
import org.redkale.source.DataSource;

/**
 *
 * @author zhangjx
 */
public class ResourceTypeTest {

    public static void main(String[] args) throws Throwable {
        ResourceTypeTest test = new ResourceTypeTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        ResourceFactory factory = ResourceFactory.create();
        factory.register(new DataSourceProvider());
        InjectBean bean = new InjectBean();
        factory.inject(bean);
    }

    public static class DataSourceProvider implements ResourceTypeLoader {

        @Override
        public Object load(
                ResourceFactory factory,
                String srcResourceName,
                Object srcObj,
                String resourceName,
                Field field,
                Object attachment) {
            DataSource source = new DataMemorySource(resourceName);
            factory.register(resourceName, DataSource.class, source);
            if (field != null) {
                try {
                    field.set(srcObj, source);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return source;
        }

        @Override
        public Type resourceType() {
            return DataSource.class;
        }
    }

    public static class InjectBean {

        @Resource(name = "platf")
        public DataSource source;
    }
}
