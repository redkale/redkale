/*
 */
package org.redkale.inject;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import org.redkale.annotation.Nullable;

/**
 * 自定义注入加载器
 *
 * <blockquote>
 * <pre>
 *
 *  public class CustomTypeLoader implements ResourceTypeLoader {
 *
 *      &#064;Override
 *      public Object load(
 *              ResourceFactory factory,
 *              String srcResourceName,
 *              Object srcObj,
 *              CustomConf annotation,
 *              Field field,
 *              Object attachment) {
 *          DataSource source = new DataMemorySource(resourceName);
 *          factory.register(resourceName, DataSource.class, source);
 *          if (field != null) {
 *              try {
 *                  field.set(srcObj, source);
 *              } catch (Exception e) {
 *                  e.printStackTrace();
 *              }
 *          }
 *          return source;
 *      }
 *
 *      &#064;Override
 *      public Type resourceType() {
 *          return DataSource.class;
 *      }
 *  }
 *
 *
 *  public class InjectBean {
 *
 *      &#064;Resource(name = "platf")
 *      public DataSource source;
 *  }
 *
 *
 *  ResourceFactory factory = ResourceFactory.create();
 *  factory.register(new CustomTypeLoader());
 *  InjectBean bean = new InjectBean();
 *  factory.inject(bean);
 *
 *
 * </pre>
 * </blockquote>
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
     * @param attachment  附加对象
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
