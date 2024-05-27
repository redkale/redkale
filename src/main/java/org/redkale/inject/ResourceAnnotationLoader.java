/*
 *
 */
package org.redkale.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * 自定义注入加载器
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
