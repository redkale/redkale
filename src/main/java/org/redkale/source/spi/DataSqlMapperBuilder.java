/*
 *
 */
package org.redkale.source.spi;

import java.lang.reflect.Field;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;

/**
 *
 * @author zhangjx
 */
public class DataSqlMapperBuilder implements ResourceTypeLoader {

    @Override
    public Object load(ResourceFactory factory, String srcResourceName, Object srcObj, String resourceName, Field field, Object attachment) {
        return null;
    }

}
