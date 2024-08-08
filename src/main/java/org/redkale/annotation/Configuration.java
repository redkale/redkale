/*

*/

package org.redkale.annotation;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * 启动服务时的初始化配置，需要结合{@link org.redkale.annotation.Resource}使用
 * <blockquote>
 * <pre>
 * &#064;Configuration
 * public class MyConfiguration {
 *
 *     &#064;Resource(name = "a")
 *     BeanA createBeanA() {
 *         System.out.println("创建一个Bean");
 *         BeanA bean = new BeanA();
 *         bean.desc = "auto";
 *         return bean;
 *     }
 *
 *     &#064;Resource(name = "b")
 *     BeanA createBeanA(&#064;Resource(name = "dev.desc") String desc) {
 *         System.out.println("创建一个Bean");
 *         BeanA bean = new BeanA();
 *         bean.desc = name;
 *         return bean;
 *     }
 * }
 *
 * </pre>
 * </blockquote>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface Configuration {}
