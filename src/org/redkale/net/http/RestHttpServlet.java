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
        response.addHeader(output.getHeaders());
        response.addCookie(output.getCookies());

        if (output.getResult() instanceof File) {
            response.finish((File) output.getResult());
        } else {
            response.finishJson(output.getResult());
        }
    }

    protected void finishJsResult(final HttpResponse response, final String var, RestOutput output) throws IOException {
        if (output != null) {
            response.addHeader(output.getHeaders());
            response.addCookie(output.getCookies());
        }
        response.finishJsResult(var, output == null ? null : output.getResult());
    }

}
