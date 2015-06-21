/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.sncp;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.net.*;
import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.net.*;
import java.util.concurrent.*;

/**
 *
 * @author zhangjx
 */
public class SncpTest {

    private static final String serviceName = "";

    private static final int port = 7070;

    public static void main(String[] args) throws Exception {

        runServer();
        runClient();
    }

    private static void runClient() throws Exception {
        ResourceFactory.root().register("", BsonConvert.class, BsonFactory.root().getConvert());
        Transport transport = new Transport("testsncp", "UDP", WatchFactory.root(), 100, new InetSocketAddress("127.0.0.1", port));
        ResourceFactory.root().register("testsncp", Transport.class, transport);
        SncpTestService service = Sncp.createRemoteService(serviceName, SncpTestService.class, "testsncp");
        ResourceFactory.root().inject(service);
        
        SncpTestBean bean = new SncpTestBean();
        StringBuilder sb = new StringBuilder();
        for(int i =0; i < 2000; i++) {
            sb.append("_").append(i).append("_0123456789");
        }
        bean.setContent(sb.toString()); 
        System.out.println(service.queryResult(bean));
    }

    private static void runServer() throws Exception {
        final CountDownLatch cdl = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                try {
                    SncpServer server = new SncpServer();
                    server.addService(new ServiceEntry(SncpTestService.class, new SncpTestService(), null, serviceName));
                    AnyValue.DefaultAnyValue conf = new AnyValue.DefaultAnyValue();
                    conf.addValue("host", "0.0.0.0");
                    conf.addValue("port", "" + port);
                    server.init(conf);
                    server.start();
                    cdl.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
        cdl.await();
    }
}
