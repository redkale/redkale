/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.*;
import java.util.concurrent.*;
import org.redkale.net.*;
import org.redkale.util.ObjectPool;

/**
 *
 * @author zhangjx
 */
public class SncpTransportFactory extends TransportFactory {

    protected final List<WeakReference<SncpClient>> clients = new CopyOnWriteArrayList<>();

    public SncpTransportFactory(ExecutorService executor, ObjectPool<ByteBuffer> bufferPool, AsynchronousChannelGroup channelGroup) {
        super(executor, bufferPool, channelGroup);
    }

    public SncpTransportFactory addGroupInfo(String name, InetSocketAddress... addrs) {
        addGroupInfo(new TransportGroupInfo(name, addrs));
        return this;
    }

    public SncpTransportFactory addGroupInfo(String name, Set<InetSocketAddress> addrs) {
        addGroupInfo(new TransportGroupInfo(name, addrs));
        return this;
    }

    void addSncpClient(SncpClient client) {
        clients.add(new WeakReference<>(client));
    }

    public List<SncpClient> getSncpClients() {
        List<SncpClient> rs = new ArrayList<>();
        for (WeakReference<SncpClient> ref : clients) {
            SncpClient client = ref.get();
            if (client != null) rs.add(client);
        }
        return rs;
    }
}
