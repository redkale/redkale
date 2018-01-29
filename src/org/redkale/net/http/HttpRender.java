/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.util.AnyValue;

/**
 * HTTP输出引擎的基类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 */
public interface HttpRender<T> {

    default void init(HttpContext context, AnyValue config) {
    }

    public <V extends T> void renderTo(HttpRequest request, HttpResponse response, V scope);

    public Class<T> getType();
}
