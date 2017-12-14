/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import java.nio.channels.CompletionHandler;
import javax.annotation.Resource;
import org.redkale.service.*;
import org.redkale.util.*;

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

    public void bcCurrentTime(final CompletionHandler<String, Void> handler, final String name) {
        cService.ccCurrentTime(Utility.createAsyncHandler((v, a) -> {
            System.out.println("执行了 BCService.bcCurrentTime----异步方法");
            String rs = "异步bcCurrentTime: " + (v == null ? null : v.getResult());
            if (handler != null) handler.completed(rs, null);
        }, (t, a) -> {
            if (handler != null) handler.failed(t, a);
        }), name);
    }

    public void bcCurrentTime(final MyAsyncHandler<String, Void> handler, final String name) {
        cService.mcCurrentTime(new MyAsyncHandler<RetResult<String>, Void>() {
            @Override
            public int id() {
                return 1;
            }

            @Override
            public void completed(RetResult<String> v, Void a) {
                System.out.println("执行了 BCService.bcCurrentTime----异步方法2");
                String rs = "异步bcCurrentTime: " + (v == null ? null : v.getResult());
                if (handler != null) handler.completed(rs, null);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
            }

            @Override
            public int id2() {
                return 2;
            }
        }, name);
    }
}
