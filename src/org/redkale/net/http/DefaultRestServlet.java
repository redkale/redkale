/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.IOException;

/**
 * 默认Servlet, 没有配置RestHttpServlet实现类则使用该默认类
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class DefaultRestServlet extends RestHttpServlet<Object> {

    @Override
    protected Object currentUser(HttpRequest req) throws IOException {
        return new Object();
    }

    @Override
    public boolean authenticate(int module, int actionid, HttpRequest request, HttpResponse response) throws IOException {
        return true;
    }

}
