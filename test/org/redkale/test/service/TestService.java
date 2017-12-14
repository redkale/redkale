/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import java.nio.channels.CompletionHandler;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class TestService implements Service {

//    public boolean change(TestBean bean, String name, int id) {
//        return false;
//    }

    public void change(CompletionHandler<Boolean, TestBean> handler, TestBean bean, String name, int id) {

    }
    
    public static void main(String[] args) throws Throwable {
        SncpServer cserver = new SncpServer();
        cserver.addSncpServlet(new TestService());
        cserver.init(AnyValue.DefaultAnyValue.create("port", 5577));
    }
}
