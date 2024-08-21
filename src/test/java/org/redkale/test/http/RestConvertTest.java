/*

*/

package org.redkale.test.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.boot.Application;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.AsyncIOGroup;
import org.redkale.net.http.HttpServer;
import org.redkale.net.http.HttpServlet;
import org.redkale.net.sncp.Sncp;
import org.redkale.util.AnyValueWriter;

/**
 *
 * @author zhangjx
 */
public class RestConvertTest {

    public static void main(String[] args) throws Throwable {
        RestConvertTest test = new RestConvertTest();
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
        resFactory.register(BsonConvert.root());
        Method method = Application.class.getDeclaredMethod("initWorkExecutor");
        method.setAccessible(true);
        method.invoke(application);

        // ------------------------ 初始化 CService ------------------------------------
        RestConvertService service = Sncp.createSimpleLocalService(RestConvertService.class, resFactory);
        HttpServer server = new HttpServer(application, System.currentTimeMillis(), resFactory);
        server.getResourceFactory().register(application);
        System.out.println("servlet = " + server.addRestServlet(null, service, null, HttpServlet.class, ""));
        server.init(AnyValueWriter.create("port", 0));
        server.start();

        int port = server.getSocketAddress().getPort();
        System.out.println("服务器启动端口: " + port);
        InetSocketAddress httpAddress = new InetSocketAddress("127.0.0.1", port);
        Socket socket = new Socket(httpAddress.getAddress(), port);
        OutputStream out = socket.getOutputStream();
        byte[] bytes = new byte[8192];
        long s, e;
        {
            out.write(("GET /test/load1  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n").getBytes());
            InputStream in = socket.getInputStream();
            s = System.currentTimeMillis();
            int pos = in.read(bytes);
            e = System.currentTimeMillis() - s;
            String rs = new String(bytes, 0, pos);
            System.out.println("返回字节: " + rs);
            System.out.println("耗时: " + e + " ms");
            String json = rs.substring(rs.lastIndexOf('\n') + 1);
            System.out.println("返回的json结果: " + json);
            Assertions.assertEquals(
                    "{\"content\":{\"createTime\":100},\"enable\":true,\"id\":123,\"name\":\"haha\"}", json);
            System.out.println("------------------1--------------------");
            Assertions.assertTrue(e < 100);
        }
        {
            out.write(("GET /test/load2  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n").getBytes());
            InputStream in = socket.getInputStream();
            s = System.currentTimeMillis();
            int pos = in.read(bytes);
            e = System.currentTimeMillis() - s;
            String rs = new String(bytes, 0, pos);
            System.out.println("返回字节: " + rs);
            System.out.println("耗时: " + e + " ms");
            String json = rs.substring(rs.lastIndexOf('\n') + 1);
            System.out.println("返回的json结果: " + json);
            Assertions.assertEquals(
                    "{\"content\":{\"aesKey\":\"keykey\",\"createTime\":100},\"enable\":true,\"id\":123,\"name\":\"haha\"}",
                    json);
            System.out.println("------------------2--------------------");
            Assertions.assertTrue(e < 100);
        }
        {
            out.write(("GET /test/load3  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n").getBytes());
            InputStream in = socket.getInputStream();
            s = System.currentTimeMillis();
            int pos = in.read(bytes);
            e = System.currentTimeMillis() - s;
            String rs = new String(bytes, 0, pos);
            System.out.println("返回字节: " + rs);
            System.out.println("耗时: " + e + " ms");
            String json = rs.substring(rs.lastIndexOf('\n') + 1);
            System.out.println("返回的json结果: " + json);
            Assertions.assertEquals("{\"id\":123}", json);
            System.out.println("-----------------3---------------------");
            Assertions.assertTrue(e < 100);
        }
        {
            out.write(("GET /test/load4  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n").getBytes());
            InputStream in = socket.getInputStream();
            s = System.currentTimeMillis();
            int pos = in.read(bytes);
            e = System.currentTimeMillis() - s;
            String rs = new String(bytes, 0, pos);
            System.out.println("返回字节: " + rs);
            System.out.println("耗时: " + e + " ms");
            String json = rs.substring(rs.lastIndexOf('\n') + 1);
            System.out.println("返回的json结果: " + json);
            Assertions.assertEquals(
                    "{\"content\":{\"createTime\":100},\"enable\":1,\"id\":123,\"name\":\"haha\"}", json);
            System.out.println("-----------------4---------------------");
            Assertions.assertTrue(e < 100);
        }
        server.shutdown();
    }
}
