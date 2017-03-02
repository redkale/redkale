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
public abstract class RestHttpServlet<T> extends HttpBaseServlet {

    protected abstract T currentUser(HttpRequest req) throws IOException;

    protected void finishJson(final HttpResponse response, RestOutput output) throws IOException {
        if (output == null) {
            response.finishJson(output);
            return;
        }
        if (output.getContentType() != null) response.setContentType(output.getContentType());
        response.addHeader(output.getHeaders());
        response.addCookie(output.getCookies());

        if (output.getResult() instanceof File) {
            response.finish((File) output.getResult());
        } else if (output.getResult() instanceof String) {
            response.finish((String) output.getResult());
        } else {
            response.finishJson(output.getResult());
        }
    }

}
