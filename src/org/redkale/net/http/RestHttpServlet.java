/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.IOException;
import org.redkale.util.Attribute;

/**
 *
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 * @param <T>
 */
public abstract class RestHttpServlet<T> extends HttpBaseServlet {

    Attribute[] _paramAttrs; // 为null表示无DynCall处理，index=0固定为null, 其他为参数标记的DynCall回调方法

    protected abstract T currentUser(HttpRequest req) throws IOException;

    protected void _callParameter(final HttpResponse response, final Object... params) {
        if (_paramAttrs == null) return;
        for (int i = 1; i < _paramAttrs.length; i++) {
            org.redkale.util.Attribute attr = _paramAttrs[i];
            if (attr == null) continue;
            
            //convert.convertTo(out, attr.type(), attr.get(params[i - 1]));
        }
    }
}
