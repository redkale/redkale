/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.io.File;
import java.lang.reflect.*;
import java.nio.channels.CompletionHandler;
import org.junit.jupiter.api.*;
import org.redkale.util.TypeToken;

/** @author zhangjx */
public class TypeTokenTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        TypeTokenTest test = new TypeTokenTest();
        test.main = true;
        test.run();
        test.run2();
    }

    @Test
    public void run() throws Exception {
        Class serviceType = Service1.class;
        Method method = serviceType.getMethod("test", String.class, CompletionHandler.class);
        Type handlerType = TypeToken.getGenericType(method.getGenericParameterTypes()[1], serviceType);
        Type resultType = null;
        if (handlerType instanceof Class) {
            resultType = Object.class;
        } else if (handlerType instanceof ParameterizedType) {
            resultType = TypeToken.getGenericType(
                    ((ParameterizedType) handlerType).getActualTypeArguments()[0], handlerType);
        }
        if (!main) {
            Assertions.assertEquals(resultType, String.class);
        }
        System.out.println("resultType = " + resultType);
    }

    @Test
    public void run2() throws Exception {
        Class serviceType = Service2.class;
        Method method = serviceType.getMethod("test", String.class, CompletionHandler.class);
        Type handlerType = TypeToken.getGenericType(method.getGenericParameterTypes()[1], serviceType);
        Type resultType = null;
        if (handlerType instanceof Class) {
            resultType = Object.class;
        } else if (handlerType instanceof ParameterizedType) {
            resultType = TypeToken.getGenericType(
                    ((ParameterizedType) handlerType).getActualTypeArguments()[0], handlerType);
        }
        if (!main) {
            Assertions.assertEquals(resultType, File.class);
        }
        System.out.println("resultType = " + resultType);
    }

    public abstract static class Service1 {

        public abstract void test(String name, CompletionHandler<String, Integer> handler);
    }

    public abstract static class IService2<T> {

        public abstract void test(String name, CompletionHandler<T, Integer> handler);
    }

    public abstract static class Service2 extends IService2<File> {}
}
