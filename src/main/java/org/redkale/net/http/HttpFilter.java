/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.net.Filter;
import org.redkale.util.AnyValue;

/**
 * HTTP 过滤器  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class HttpFilter extends Filter<HttpContext, HttpRequest, HttpResponse> {

    //Server执行start后运行此方法
    public void postStart(HttpContext context, AnyValue config) {
    }
}
