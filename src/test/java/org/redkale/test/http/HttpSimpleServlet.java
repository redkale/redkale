/*
 *
 */
package org.redkale.test.http;

import java.io.IOException;
import org.redkale.net.http.HttpMapping;
import org.redkale.net.http.HttpRequest;
import org.redkale.net.http.HttpResponse;
import org.redkale.net.http.HttpServlet;

/** @author zhangjx */
public class HttpSimpleServlet extends HttpServlet {

    @HttpMapping(url = "/test")
    public void test(HttpRequest req, HttpResponse resp) throws IOException {
        System.out.println("运行到test方法了， id=" + req.getParameter("id"));
        resp.finish("ok-" + req.getParameter("id", "0"));
    }
}
