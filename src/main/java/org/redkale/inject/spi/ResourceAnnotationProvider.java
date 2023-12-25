/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.inject.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import org.redkale.inject.ResourceFactory;

/**
 * 自定义注入加载器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> Annotation
 */
public interface ResourceAnnotationProvider<T extends Annotation> {

    public void load(ResourceFactory factory, String srcResourceName, Object srcObj, T annotation, Field field, Object attachment);

    public Class<T> annotationType();
}
