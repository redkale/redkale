/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.convert.*;
import org.redkale.util.*;

/**
 * HTTP输出引擎的基类 <br>
 * HttpRender主要是给HttpResponse.finish(Object obj)提供指定数据类型的输出策略。 <br>
 *
 * <pre>
 * HttpResponse.finish(Object obj)内置对如下数据类型进行了特殊处理:
 *      CompletionStage
 *      CharSequence/String
 *      byte[]
 *      File
 *      RetResult
 *      HttpResult
 *      HttpScope
 * </pre>
 *
 * <p>如果对其他数据类型有特殊输出的需求，则需要自定义HttpRender。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface HttpRender {

	default void init(HttpContext context, AnyValue config) {}

	public void renderTo(HttpRequest request, HttpResponse response, Convert convert, HttpScope scope);

	default void destroy(HttpContext context, AnyValue config) {}
}
