/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import java.nio.channels.CompletionHandler;
import org.redkale.annotation.Resource;
import org.redkale.service.*;
import org.redkale.util.Utility;

/** @author zhangjx */
public class BCService implements Service {

    @Resource
    private CService cService;

    @Resource(name = "@name")
    private String serviceName;

    @Resource(name = "@type")
    private Class serviceType;

    public String serviceName() {
        return serviceName;
    }

    public Class serviceType() {
        return serviceType;
    }

    public String bcCurrentTime1(final String name) {
        System.out.println("准备执行BCService.bcCurrentTime1方法");
        String rs = "同步bcCurrentTime1: " + cService.ccCurrentTime1(name).getResult();
        System.out.println("执行了 BCService.bcCurrentTime1++++同步方法1");
        return rs;
    }

    public void bcCurrentTime2(final CompletionHandler<String, Void> handler, final String name) {
        cService.ccCurrentTime2(
                Utility.createAsyncHandler(
                        (v, a) -> {
                            System.out.println("执行了 BCService.bcCurrentTime2----异步方法2");
                            String rs = "异步bcCurrentTime2: " + (v == null ? null : v.getResult());
                            if (handler != null) {
                                handler.completed(rs, null);
                            }
                        },
                        (t, a) -> {
                            if (handler != null) {
                                handler.failed(t, a);
                            }
                        }),
                name);
    }

    public void bcCurrentTime3(final MyAsyncHandler<String, Void> handler, final String name) {
        cService.mcCurrentTime3(
                new MyAsyncHandler<RetResult<String>, Void>() {
                    @Override
                    public int id() {
                        return 1;
                    }

                    @Override
                    public void completed(RetResult<String> v, Void a) {
                        System.out.println("执行了 BCService.bcCurrentTime3----异步方法3");
                        String rs = "异步bcCurrentTime3: " + (v == null ? null : v.getResult());
                        if (handler != null) {
                            handler.completed(rs, null);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment) {}

                    @Override
                    public int id2() {
                        return 2;
                    }
                },
                name);
    }
}
