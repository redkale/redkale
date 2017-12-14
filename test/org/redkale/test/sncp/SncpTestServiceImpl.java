/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.*;
import org.redkale.net.TransportFactory;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.source.DataCallArrayAttribute;
import static org.redkale.test.sncp.SncpTest.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
@ResourceType(SncpTestIService.class)
public class SncpTestServiceImpl implements SncpTestIService {

    @Override
    public CompletableFuture<String> queryResultAsync(SncpTestBean bean) {
        final CompletableFuture<String> future = new CompletableFuture<>();
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                    System.out.println(Thread.currentThread().getName() + " 运行了异步方法-----------queryResultAsync方法");
                    future.complete("异步result: " + bean);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return future;

    }

    @Override
    @RpcMultiRun
    public long queryLongResult(String a, int b, long value) {
        return value + 1;
    }

    @Override
    @RpcMultiRun
    public double queryDoubleResult(String a, int b, double value) {
        return value + 1;
    }
    
    public static class CallAttribute implements Attribute<SncpTestBean, Long> {

        @Override
        public Class<? extends Long> type() {
            return long.class;
        }

        @Override
        public Class<SncpTestBean> declaringClass() {
            return SncpTestBean.class;
        }

        @Override
        public String field() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Long get(SncpTestBean obj) {
            System.out.println("返回ID: " + obj.getId());
            return obj.getId();
        }

        @Override
        public void set(SncpTestBean obj, Long value) {
            System.out.println("设置ID: " + value);
            obj.setId(value);
        }

    }

    @Override
    public void insert(@RpcCall(DataCallArrayAttribute.class) SncpTestBean... beans) {
        for (SncpTestBean bean : beans) {
            bean.setId(System.currentTimeMillis());
        }
    }

    @Override
    public String queryResult(SncpTestBean bean) {
        System.out.println(Thread.currentThread().getName() + " 运行了queryResult方法");
        return "result: " + bean;
    }

    public void queryResult(CompletionHandler<String, SncpTestBean> handler, @RpcAttachment SncpTestBean bean) {
        System.out.println(Thread.currentThread().getName() + " handler 运行了queryResult方法");
        if (handler != null) handler.completed("result: " + bean, bean);
    }

    @RpcMultiRun
    @Override
    public String updateBean(@RpcCall(CallAttribute.class) SncpTestBean bean) {
        bean.setId(System.currentTimeMillis());
        System.out.println(Thread.currentThread().getName() + " 运行了updateBean方法");
        return "result: " + bean;
    }

    public static void main(String[] args) throws Exception {

        final TransportFactory transFactory = TransportFactory.create(Executors.newSingleThreadExecutor(), newBufferPool(), newChannelGroup());

        transFactory.addGroupInfo("g70", new InetSocketAddress("127.0.0.1", 7070));
        Service service = Sncp.createSimpleLocalService(SncpTestServiceImpl.class, transFactory, new InetSocketAddress("127.0.0.1", 7070), "g70");
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : SncpClient.parseMethod(service.getClass())) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        service = Sncp.createSimpleRemoteService(SncpTestServiceImpl.class, transFactory, new InetSocketAddress("127.0.0.1", 7070), "g70");
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : SncpClient.parseMethod(service.getClass())) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        service = Sncp.createSimpleRemoteService(SncpTestIService.class, transFactory, new InetSocketAddress("127.0.0.1", 7070), "g70");
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : SncpClient.parseMethod(service.getClass())) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
    }
}
