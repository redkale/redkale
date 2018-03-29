/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.net;

import java.net.InetSocketAddress;
import java.util.*;
import org.redkale.net.*;
import org.redkale.net.http.HttpServer;
import org.redkale.net.sncp.Sncp;
import org.redkale.util.AnyValue.DefaultAnyValue;

/**
 *
 * @author zhangjx
 */
public class TransportTest {

    private static final String format = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL";

    public static void main(String[] args) throws Throwable {

        List<InetSocketAddress> addrs = new ArrayList<>();
        addrs.add(new InetSocketAddress("127.0.0.1", 22001));
        addrs.add(new InetSocketAddress("127.0.0.1", 22002));
        addrs.add(new InetSocketAddress("127.0.0.1", 22003));
        addrs.add(new InetSocketAddress("127.0.0.1", 22004));
        for (InetSocketAddress servaddr : addrs) {
            //if (servaddr.getPort() % 100 == 4) continue;
            HttpServer server = new HttpServer();
            DefaultAnyValue servconf = DefaultAnyValue.create("port", servaddr.getPort());
            server.init(servconf);
            server.start();
        }
        addrs.add(new InetSocketAddress("127.0.0.1", 22005));
        Thread.sleep(1000);
        TransportFactory factory = TransportFactory.create(10);
        DefaultAnyValue conf = DefaultAnyValue.create(TransportFactory.NAME_PINGINTERVAL, 5);
        factory.init(conf, Sncp.PING_BUFFER, Sncp.PONG_BUFFER.remaining());
        Transport transport = factory.createTransportTCP("", null, addrs);
        System.out.println(String.format(format, System.currentTimeMillis()));
        try {
            AsyncConnection firstconn = transport.pollConnection(null).join();
            System.out.println(firstconn);
            if (firstconn != null) transport.offerConnection(false, firstconn);
            AsyncConnection conn = transport.pollConnection(null).join();
            System.out.println(conn + "-------应该与前值相同");
            conn = transport.pollConnection(null).join();
            System.out.println(conn + "-------应该与前值不同");
        } finally {
            System.out.println(String.format(format, System.currentTimeMillis()));
        }
    }

}
