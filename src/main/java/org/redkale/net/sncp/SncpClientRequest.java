/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import java.util.Objects;
import org.redkale.net.client.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class SncpClientRequest extends ClientRequest {

    private final InetSocketAddress clientSncpAddress;

    private final byte[] addrBytes;

    private final int addrPort;

    private long seqid;

    private Uint128 serviceid;

    private int serviceVersion;

    private Uint128 actionid;

    private byte[] bodyContent;

    public SncpClientRequest(InetSocketAddress clientSncpAddress) {
        this.clientSncpAddress = clientSncpAddress;
        this.addrBytes = clientSncpAddress == null ? new byte[4] : clientSncpAddress.getAddress().getAddress();
        this.addrPort = clientSncpAddress == null ? 0 : clientSncpAddress.getPort();
    }

    public SncpClientRequest prepare(long seqid, Uint128 serviceid, int serviceVersion, Uint128 actionid, String traceid, byte[] bodyContent) {
        super.prepare();
        this.seqid = seqid;
        this.serviceid = serviceid;
        this.serviceVersion = serviceVersion;
        this.actionid = actionid;
        this.traceid = traceid;
        this.bodyContent = bodyContent;
        return this;
    }

    @Override
    protected boolean recycle() {
        boolean rs = super.recycle();
        this.seqid = 0;
        this.serviceVersion = 0;
        this.serviceid = null;
        this.actionid = null;
        this.bodyContent = null;
        return rs;
    }

    @Override
    public void writeTo(ClientConnection conn, ByteArray array) {
        
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "_" + Objects.hashCode(this) + "{"
            + "seqid = " + seqid
            + ", serviceVersion = " + serviceVersion
            + ", serviceid = " + serviceid
            + ", actionid = " + actionid
            + ", bodyLength = " + (bodyContent == null ? -1 : bodyContent.length)
            + "}";
    }

    public long getSeqid() {
        return seqid;
    }

    public Uint128 getServiceid() {
        return serviceid;
    }

    public int getServiceVersion() {
        return serviceVersion;
    }

    public Uint128 getActionid() {
        return actionid;
    }

    public InetSocketAddress getClientSncpAddress() {
        return clientSncpAddress;
    }

    public byte[] getBodyContent() {
        return bodyContent;
    }

}
