/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.util.AnyValue;

/**
 * HTTP模板引擎的基类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface HttpTemplateEngine {

    default void init(HttpContext context, AnyValue config) {
    }

    public void renderTo(HttpResponse response, HttpScope scope);
}
