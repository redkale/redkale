/*
 *
 */
package org.redkale.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * 自定义注入加载器
 *
 * <blockquote>
 * <pre>
 *
 * &#064;Documented
 * &#064;Target({FIELD})
 * &#064;Retention(RUNTIME)
 * public @interface CustomConf {
 *     String path();
 * }
 *
 *
 *  public class CustomConfProvider implements ResourceAnnotationLoader&lt;CustomConf&gt; {
 *
 *      &#064;Override
 *      public void load(
 *              ResourceFactory factory,
 *              String srcResourceName,
 *              Object srcObj,
 *              CustomConf annotation,
 *              Field field,
 *              Object attachment) {
 *          try {
 *              field.set(srcObj, new File(annotation.path()));
 *          } catch (Exception e) {
 *              e.printStackTrace();
 *          }
 *          System.out.println("对象是 src =" + srcObj + ", path=" + annotation.path());
 *      }
 *
 *      &#064;Override
 *      public Class&lt;CustomConf&gt; annotationType() {
 *          return CustomConf.class;
 *      }
 *  }
 *
 *
 *  public class InjectBean {
 *
 *      &#064;CustomConf(path = "conf/test.xml")
 *      public File conf;
 *  }
 *
 *
 * </pre>
 * </blockquote>
 *
 * <p>详情见: https://redkale.org
 *
 * @since 2.8.0
 * @author zhangjx
 * @param <T> Annotation
 */
public interface ResourceAnnotationLoader<T extends Annotation> {

    public void load(
            ResourceFactory factory,
            String srcResourceName,
            Object srcObj,
            T annotation,
            Field field,
            Object attachment);

    public Class<T> annotationType();
}
