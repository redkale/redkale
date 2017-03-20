/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import org.redkale.service.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class CService implements Service {

    public RetResult<String> ccCurrentTime(final String name) {
        String rs = "同步ccCurrentTime: " + name + ": " + Utility.formatTime(System.currentTimeMillis());
        System.out.println("执行了 CService.ccCurrentTime++++同步方法");
        return new RetResult(rs);
    }

    public void ccCurrentTime(final AsyncHandler<RetResult<String>, Void> handler, final String name) {
        String rs = "异步ccCurrentTime: " + name + ": " + Utility.formatTime(System.currentTimeMillis());
        System.out.println("执行了 CService.ccCurrentTime----异步方法");
        if (handler != null) handler.completed(new RetResult(rs), null);
    }
}
