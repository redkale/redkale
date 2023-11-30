/*
 *
 */
package org.redkale.test.http;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.mq.HttpSimpleRequestCoder;
import org.redkale.net.client.ClientRequest;
import org.redkale.net.http.HttpSimpleRequest;

/**
 *
 * @author zhangjx
 */
public class RequestCoderTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        RequestCoderTest test = new RequestCoderTest();
        test.main = true;
        test.run1();
        test.run2();
        test.run3();
    }

    @Test
    public void run1() throws Exception {
        HttpSimpleRequest req1 = HttpSimpleRequest.createPostPath("/aaa");
        System.out.println("simpleRequest1: " + req1);
        byte[] bytes = HttpSimpleRequestCoder.getInstance().encode(req1);
        HttpSimpleRequest req2 = HttpSimpleRequestCoder.getInstance().decode(bytes);
        Field timeFiedl = ClientRequest.class.getDeclaredField("createTime");
        timeFiedl.setAccessible(true);
        timeFiedl.set(req2, req1.getCreateTime());
        System.out.println("simpleRequest2: " + req2);
        Assertions.assertEquals(req1.toString(), req2.toString());
    }

    @Test
    public void run2() throws Exception {
        HttpSimpleRequest req1 = HttpSimpleRequest.createPostPath("/aaa");
        req1.addHeader("X-aaa", "aaa");
        req1.param("bean", "{}");
        System.out.println("simpleRequest1: " + req1);
        byte[] bytes = HttpSimpleRequestCoder.getInstance().encode(req1);
        HttpSimpleRequest req2 = HttpSimpleRequestCoder.getInstance().decode(bytes);
        Field timeFiedl = ClientRequest.class.getDeclaredField("createTime");
        timeFiedl.setAccessible(true);
        timeFiedl.set(req2, req1.getCreateTime());
        System.out.println("simpleRequest2: " + req2);
        Assertions.assertEquals(req1.toString(), req2.toString());
    }

    @Test
    public void run3() throws Exception {
        HttpSimpleRequest req1 = HttpSimpleRequest.createPostPath("/aaa");
        req1.addHeader("X-aaa", "aaa");
        req1.addHeader("X-bbb", "bbb1");
        req1.addHeader("X-bbb", "bbb2");
        req1.param("bean", "{}");
        System.out.println("simpleRequest1: " + req1);
        byte[] bytes = HttpSimpleRequestCoder.getInstance().encode(req1);
        HttpSimpleRequest req2 = HttpSimpleRequestCoder.getInstance().decode(bytes);
        Field timeFiedl = ClientRequest.class.getDeclaredField("createTime");
        timeFiedl.setAccessible(true);
        timeFiedl.set(req2, req1.getCreateTime());
        System.out.println("simpleRequest2: " + req2);
        Assertions.assertEquals(req1.toString(), req2.toString());
    }
}
