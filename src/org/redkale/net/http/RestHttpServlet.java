/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;

/**
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 当前用户对象类型
 */
public class RestHttpServlet<T> extends HttpServlet {

    protected T currentUser(HttpRequest req) throws IOException {
        return null;
    }

}
