/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import org.redkale.annotation.ResourceType;
import org.redkale.boot.Application;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.AsyncIOGroup;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.Utility;

/** @author zhangjx */
@ResourceType(SncpTestIService.class)
public class SncpTestServiceImpl implements SncpTestIService {

    @Override
    public CompletableFuture<String> queryResultAsync(SncpTestBean bean) {
        final CompletableFuture<String> future = new CompletableFuture<>();
        new Thread() {
            @Override
            public void run() {
                try {
                    Utility.sleep(200);
                    System.out.println(
                            Thread.currentThread().getName() + " sleep 200ms后运行了异步方法-----------queryResultAsync方法");
                    future.complete("异步 result: " + bean);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return future;
    }

    @Override
    public long queryLongResult(String a, int b, long value) {
        return value + 1;
    }

    @Override
    public double queryDoubleResult(String a, int b, double value) {
        return value + 1;
    }

    @Override
    public SncpTestBean insert(SncpTestBean bean) {
        bean.setId(System.currentTimeMillis());
        return bean;
    }

    public SncpTestBean expand(SncpTestBean bean) {
        bean.setId(System.currentTimeMillis());
        return bean;
    }

    @Override
    public String queryResult(SncpTestBean bean) {
        System.out.println(Thread.currentThread().getName() + " 运行了queryResult方法 content-length: "
                + bean.getContent().length());
        return "result-content:   " + bean.getContent();
    }

    public void queryResult(CompletionHandler<String, SncpTestBean> handler, @RpcAttachment SncpTestBean bean) {
        System.out.println(Thread.currentThread().getName() + " handler 运行了queryResult方法");
        if (handler != null) {
            handler.completed("result: " + bean.getId(), bean);
        }
    }

    @Override
    public String updateBean(SncpTestBean bean) {
        bean.setId(System.currentTimeMillis());
        System.out.println(Thread.currentThread().getName() + " 运行了updateBean方法");
        return "result: " + bean;
    }

    public static void main(String[] args) throws Exception {

        final Application application = Application.create(true);
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        asyncGroup.start();
        final ResourceFactory factory = ResourceFactory.create();
        final SncpRpcGroups rpcGroups = application.getSncpRpcGroups();
        InetSocketAddress sncpAddress = new InetSocketAddress("127.0.0.1", 7070);
        rpcGroups.computeIfAbsent("g70", "TCP").putAddress(sncpAddress);
        final SncpClient client =
                new SncpClient("", asyncGroup, "0", sncpAddress, new ClientAddress(sncpAddress), "TCP", 16, 100);

        Service service =
                Sncp.createSimpleLocalService(application.getClassLoader(), SncpTestServiceImpl.class, factory);
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : Sncp.loadRemoteMethodActions(service.getClass()).values()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        service = Sncp.createSimpleRemoteService(
                application.getClassLoader(), SncpTestServiceImpl.class, factory, rpcGroups, client, "g70");
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : Sncp.loadRemoteMethodActions(service.getClass()).values()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        service = Sncp.createSimpleRemoteService(
                application.getClassLoader(), SncpTestIService.class, factory, rpcGroups, client, "g70");
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : Sncp.loadRemoteMethodActions(service.getClass()).values()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
    }
}
