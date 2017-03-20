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

    public String showCurrentTime(final String name) {
        String rs = "同步showCurrentTime: " + cService.getCurrentTime(name).getResult();
        System.out.println("执行了 BCService.showCurrentTime++++同步方法");
        return rs;
    }

    public void showCurrentTime(final AsyncHandler<String, Void> handler, final String name) {
        cService.getCurrentTime(AsyncHandler.create((v, a) -> {
            System.out.println("执行了 BCService.showCurrentTime----异步方法");
            String rs = "异步showCurrentTime: " + v.getResult();
            if (handler != null) handler.completed(rs, null);
        }, (t, a) -> {
        }), name);
    }
}
