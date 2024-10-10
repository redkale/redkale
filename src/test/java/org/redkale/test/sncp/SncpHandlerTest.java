/*
 *
 */
package org.redkale.test.sncp;

import java.io.File;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.redkale.net.sncp.SncpAsyncHandler;

/** @author zhangjx */
public class SncpHandlerTest {

    public static void main(String[] args) throws Throwable {
        SncpHandlerTest test = new SncpHandlerTest();

        test.run();
    }

    @Test
    public void run() throws Exception {
        SncpAsyncHandler.createHandler(CompletionHandler.class, new CompletionHandler() {
                    @Override
                    public void completed(Object result, Object attachment) {
                        System.out.println("handler result: " + result + ", attachment: " + attachment);
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {}
                })
                .completed(1, 2);

        SncpAsyncHandler.createHandler(ITestHandler1.class, new CompletionHandler() {
                    @Override
                    public void completed(Object result, Object attachment) {
                        System.out.println("handler1 result: " + result + ", attachment: " + attachment);
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {}
                })
                .completed("name", "/user/");

        SncpAsyncHandler.createHandler(ITestHandler2.class, new CompletionHandler() {
                    @Override
                    public void completed(Object result, Object attachment) {
                        System.out.println("handler2 result: " + result + ", attachment: " + attachment);
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {}
                })
                .completed("aaa", "bbb");

        SncpAsyncHandler.createHandler(ITestHandler3.class, new CompletionHandler() {
                    @Override
                    public void completed(Object result, Object attachment) {
                        System.out.println("handler3 result: " + result + ", attachment: " + attachment);
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {}
                })
                .completed("key1", "val1");
    }

    public abstract static class ITestHandler1 implements CompletionHandler<String, File> {

        @Override
        public abstract void completed(String result, File attachment);
    }

    public static interface IClose<T> {

        public void close(T val);
    }

    public static interface ITestHandler2 extends CompletionHandler<String, String>, IClose<File> {}

    public static interface ITestHandler3 extends CompletionHandler<String, String>, Map<String, String> {}
}
