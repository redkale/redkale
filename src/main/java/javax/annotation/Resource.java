/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javax.annotation;

import java.lang.annotation.*;

/**
 * @since Common Annotations 1.0
 *
 * @deprecated replace by org.redkale.annotation.Resource
 */
@Deprecated(since = "2.8.0")
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Resource {

//    /**
//     * AuthenticationType
//     */
//    @Deprecated
//    public enum AuthenticationType {
//        /**
//         * @deprecated
//         */
//        CONTAINER,
//        /**
//         * @deprecated
//         */
//        APPLICATION
//    }
//    
    /**
     * 资源名称
     *
     * @return String
     */
    public String name() default "";

    /**
     * 依赖注入的类型
     *
     * @return Class
     */
    public Class<?> type() default Object.class;
//
//    /**
//     *
//     * @return AuthenticationType
//     */
//    @Deprecated
//    public AuthenticationType authenticationType() default AuthenticationType.CONTAINER;
//
//    /**
//     *
//     * @return boolean
//     */
//    @Deprecated
//    public boolean shareable() default true;
//
//    /**
//     *
//     * @return String
//     */
//    @Deprecated
//    public String description() default "";
//
//    /**
//     *
//     * @return String
//     */
//    @Deprecated
//    public String mappedName() default "";
//
//    /**
//     *
//     * @return String
//     */
//    @Deprecated
//    public String lookup() default "";
}
