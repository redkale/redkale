/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import org.redkale.net.Context;

/**
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpContext extends Context {

    protected byte[] serverAddressBytes;

    protected int serverAddressPort;

    public SncpContext(SncpContextConfig config) {
        super(config);
        this.serverAddressBytes = serverAddress.getAddress().getAddress();
        this.serverAddressPort = serverAddress.getPort();
    }

    @Override
    protected void updateServerAddress(InetSocketAddress addr) {
        super.updateServerAddress(addr);
        this.serverAddressBytes = addr.getAddress().getAddress();
        this.serverAddressPort = addr.getPort();
    }

    public static class SncpContextConfig extends ContextConfig {

    }
}
