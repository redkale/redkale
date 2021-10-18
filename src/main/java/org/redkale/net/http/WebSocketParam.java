/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Arrays;

/**
 *
 * 供WebSocket.preOnMessage 方法获取RestWebSocket里OnMessage方法的参数  <br>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface WebSocketParam {

    public <T> T getValue(String name);

    public String[] getNames();

    public Annotation[] getAnnotations();

    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        for (Annotation ann : getAnnotations()) {
            if (ann.getClass() == annotationClass) return (T) ann;
        }
        return null;
    }

    default <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        Annotation[] annotations = getAnnotations();
        if (annotations == null) return (T[]) Array.newInstance(annotationClass, 0);
        T[] news = (T[]) Array.newInstance(annotationClass, annotations.length);
        int index = 0;
        for (Annotation ann : annotations) {
            if (ann.getClass() == annotationClass) {
                news[index++] = (T) ann;
            }
        }
        if (index < 1) return (T[]) Array.newInstance(annotationClass, 0);
        return Arrays.copyOf(news, index);
    }
}
