/*
 */
package org.redkale.inject;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import org.redkale.annotation.Nullable;

/**
 * 自定义注入加载器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface ResourceTypeLoader {

    /**
     * 自定义的对象注入， 实现需要兼容Field为null的情况
     *
     * @param factory ResourceFactory
     * @param srcResourceName 依附对象的资源名
     * @param srcObj  依附对象
     * @param resourceName  资源名
     * @param field  字段对象
     * @param attachment
     * @return Object
     */
    public Object load(
            ResourceFactory factory,
            @Nullable String srcResourceName,
            @Nullable Object srcObj,
            String resourceName,
            @Nullable Field field,
            @Nullable Object attachment);
    /**
     * 注入加载器对应的类型
     *
     * @return  类型
     */
    public Type resourceType();

    /**
     * 是否注入默认值null <br>
     * 返回true:  表示调用ResourceLoader之后资源仍不存在，则会在ResourceFactory里注入默认值null。 <br>
     * 返回false: 表示资源不存在下次仍会调用{@link org.redkale.inject.ResourceTypeLoader}自行处理。 <br>
     *
     * @return 是否注入默认值null
     */
    default boolean autoNone() {
        return true;
    }
}
