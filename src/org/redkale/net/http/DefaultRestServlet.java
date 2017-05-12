/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.IOException;

/**
 * 默认Servlet, 没有配置RestServlet实现类则使用该默认类
 * <p>
 * 详情见: https://redkale.org
 *
 * @deprecated
 * @author zhangjx
 */
@Deprecated
public class DefaultRestServlet extends RestHttpServlet<Object> {

    @Override
    protected Object currentUser(HttpRequest req) throws IOException {
        return new Object();
    }

}
