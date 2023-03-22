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

/**
 *
 * @author zhangjx
 */
public class CService implements Service {

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

    public RetResult<String> ccCurrentTime1(final String name) {
        String rs = "同步ccCurrentTime1: " + name + ": " + Utility.formatTime(System.currentTimeMillis());
        System.out.println("执行了 CService.ccCurrentTime1++++同步方法1");
        return new RetResult(rs);
    }

    public void ccCurrentTime2(final CompletionHandler<RetResult<String>, Void> handler, final String name) {
        String rs = "异步ccCurrentTime2: " + name + ": " + Utility.formatTime(System.currentTimeMillis());
        System.out.println("执行了 CService.ccCurrentTime2----异步方法2");
        if (handler != null) handler.completed(new RetResult(rs), null);
    }
    
    public void mcCurrentTime3(final MyAsyncHandler<RetResult<String>, Void> handler, final String name) {
        String rs = "异步mcCurrentTime3: " + name + ": " + Utility.formatTime(System.currentTimeMillis());
        System.out.println("执行了 CService.mcCurrentTime3----异步方法3");
        if (handler != null) handler.completed(new RetResult(rs), null);
    }
}
