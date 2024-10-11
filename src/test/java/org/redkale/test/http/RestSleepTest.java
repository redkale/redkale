/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.http;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import org.junit.jupiter.api.*;
import org.redkale.boot.Application;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.AsyncIOGroup;
import org.redkale.net.http.*;
import org.redkale.net.sncp.Sncp;
import org.redkale.util.*;

/** @author zhangjx */
public class RestSleepTest {

    public static void main(String[] args) throws Throwable {
        RestSleepTest test = new RestSleepTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        System.out.println("------------------- 并发调用 -----------------------------------");
        final Application application = Application.create(true);
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        asyncGroup.start();
        final ResourceFactory resFactory = ResourceFactory.create();
        resFactory.register(JsonConvert.root());
        resFactory.register(ProtobufConvert.root());
        Method method = Application.class.getDeclaredMethod("initWorkExecutor");
        method.setAccessible(true);
        method.invoke(application);

        // ------------------------ 初始化 CService ------------------------------------
        RedkaleClassLoader classLoader = RedkaleClassLoader.getRedkaleClassLoader();
        RestSleepService service =
                Sncp.createSimpleLocalService(application.getClassLoader(), RestSleepService.class, resFactory);
        HttpServer server = new HttpServer(application, System.currentTimeMillis(), resFactory);
        server.getResourceFactory().register(application);
        System.out.println("servlet = " + server.addRestServlet(classLoader, service, null, HttpServlet.class, ""));
        server.init(AnyValueWriter.create("port", 0));
        server.start();

        int port = server.getSocketAddress().getPort();
        System.out.println("服务器启动端口: " + port);
        InetSocketAddress httpAddress = new InetSocketAddress("127.0.0.1", port);
        Socket socket = new Socket(httpAddress.getAddress(), port);
        OutputStream out = socket.getOutputStream();
        out.write(("GET /test/sleep200  HTTP/1.1\r\n"
                        + "Connection: Keep-Alive\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n"
                        + "GET /test/sleep300  HTTP/1.1\r\n"
                        + "Connection: Keep-Alive\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n"
                        + "GET /test/sleep500  HTTP/1.1\r\n"
                        + "Connection: Keep-Alive\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n"
                        + "GET /test/sleepName  HTTP/1.1\r\n"
                        + "Connection: Keep-Alive\r\n"
                        + "Transfer-Encoding: chunked\r\n"
                        + "\r\n"
                        + "4\r\n"
                        + "this\r\n"
                        + "6\r\n"
                        + " is a \r\n"
                        + "5\r\n"
                        + "name!\r\n"
                        + "0\r\n"
                        + "\r\n")
                .getBytes());
        InputStream in = socket.getInputStream();
        byte[] bytes = new byte[8192];
        long s = System.currentTimeMillis();
        int pos = in.read(bytes);
        long e = System.currentTimeMillis() - s;
        System.out.println("返回结果: " + new String(bytes, 0, pos));
        System.out.println("耗时: " + e + " ms");
        server.shutdown();
        Assertions.assertTrue(e < 600);
    }
}
