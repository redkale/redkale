/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.socks;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class SocksConnectServlet extends SocksServlet {

    private InetSocketAddress bindAddress;

    private byte[] bindAddressBytes = new byte[0];

    @Override
    public void init(Context context, AnyValue config) {
        if (config == null) {
            this.bindAddress = new InetSocketAddress(Utility.localInetAddress(), context.getServerAddress().getPort());
        } else {
            this.bindAddress = new InetSocketAddress(config.getValue("bindaddr", Utility.localInetAddress().getHostAddress()), context.getServerAddress().getPort());
        }
        Logger logger = context.getLogger();
        if (logger.isLoggable(Level.INFO)) logger.info("[" + Thread.currentThread().getName() + "] bindAddress = " + bindAddress);
        ByteBuffer bb;
        InetAddress addr = bindAddress.getAddress();
        if (addr instanceof Inet6Address) {
            bb = ByteBuffer.allocate(1 + 16 + 2);
            bb.put((byte) 0x04);
        } else {
            bb = ByteBuffer.allocate(1 + 4 + 2);
            bb.put((byte) 0x01);
        }
        bb.put(addr.getAddress());
        bb.putChar((char) bindAddress.getPort());
        bb.flip();
        this.bindAddressBytes = bb.array();
    }

    @Override
    public void execute(SocksRequest request, SocksResponse response) throws IOException {
        response.getContext().submit(new SocksRunner(response.getContext(), response.removeChannel(), bindAddressBytes));
        response.finish(true);
    }

}
