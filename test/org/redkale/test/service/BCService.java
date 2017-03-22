/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import javax.annotation.Resource;
import org.redkale.service.Service;
import org.redkale.util.AsyncHandler;

/**
 *
 * @author zhangjx
 */
public class BCService implements Service {

    @Resource
    private CService cService;

    public String bcCurrentTime(final String name) {
        String rs = "同步bcCurrentTime: " + cService.ccCurrentTime(name).getResult();
        System.out.println("执行了 BCService.bcCurrentTime++++同步方法");
        return rs;
    }

    public void bcCurrentTime(final AsyncHandler<String, Void> handler, final String name) {
        cService.ccCurrentTime(AsyncHandler.create((v, a) -> {
            System.out.println("执行了 BCService.bcCurrentTime----异步方法");
            String rs = "异步bcCurrentTime: " + (v == null ? null : v.getResult());
            if (handler != null) handler.completed(rs, null);
        }, (t, a) -> {
            if (handler != null) handler.failed(t, a);
        }), name);
    }
    
    public void bcCurrentTime(final MyAsyncHandler<String, Void> handler, final String name) {
        cService.ccCurrentTime(AsyncHandler.create((v, a) -> {
            System.out.println("执行了 BCService.bcCurrentTime----异步方法2");
            String rs = "异步bcCurrentTime: " + (v == null ? null : v.getResult());
            if (handler != null) handler.completed(rs, null);
        }, (t, a) -> {
            if (handler != null) handler.failed(t, a);
        }), name);
    }
}
