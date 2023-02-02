/*
 *
 */
package org.redkale.net.sncp;

import java.io.Serializable;
import java.nio.ByteBuffer;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class SncpClientResult {

    private long seqid;

    private Uint128 serviceid;

    private int serviceVersion;

    private Uint128 actionid;

    private byte[] addrBytes;

    private int addrPort;

    private int bodyLength;

    private byte[] bodyContent;

    private int retcode;

    protected void readHeader(ByteBuffer buffer) {
        this.seqid = buffer.getLong();  //8
        buffer.getChar(); //HEADER_SIZE   2
        this.serviceid = Uint128.read(buffer); //16
        this.serviceVersion = buffer.getInt(); //4
        this.actionid = Uint128.read(buffer); //16
        this.addrBytes = new byte[4];
        buffer.get(this.addrBytes); //addr      4
        this.addrPort = buffer.getChar(); //port 2
        this.bodyLength = buffer.getInt(); //4
        this.retcode = buffer.getInt(); //4
    }

    protected void readHeader(ByteArray array) {
        int offset = 0;
        this.seqid = array.getLong(offset);  //8
        offset += 8;
        array.getChar(offset); //HEADER_SIZE   2
        offset += 2;
        this.serviceid = array.getUint128(offset); //16
        offset += 16;
        this.serviceVersion = array.getInt(offset); //4
        offset += 4;
        this.actionid = array.getUint128(offset); //16        
        offset += 16;
        this.addrBytes = array.getBytes(offset, 4); //addr      4        
        offset += 4;
        this.addrPort = array.getChar(offset); //port 2        
        offset += 2;
        this.bodyLength = array.getInt(offset); //4        
        offset += 4;
        this.retcode = array.getInt(offset); //4        
    }

    public Serializable getRequestid() {
        return seqid;
    }

    public long getSeqid() {
        return seqid;
    }

    public void setSeqid(long seqid) {
        this.seqid = seqid;
    }

    public Uint128 getServiceid() {
        return serviceid;
    }

    public void setServiceid(Uint128 serviceid) {
        this.serviceid = serviceid;
    }

    public int getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(int serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public Uint128 getActionid() {
        return actionid;
    }

    public void setActionid(Uint128 actionid) {
        this.actionid = actionid;
    }

    public byte[] getAddrBytes() {
        return addrBytes;
    }

    public void setAddrBytes(byte[] addrBytes) {
        this.addrBytes = addrBytes;
    }

    public int getAddrPort() {
        return addrPort;
    }

    public void setAddrPort(int addrPort) {
        this.addrPort = addrPort;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    public void setBodyLength(int bodyLength) {
        this.bodyLength = bodyLength;
    }

    public byte[] getBodyContent() {
        return bodyContent;
    }

    public void setBodyContent(byte[] bodyContent) {
        this.bodyContent = bodyContent;
    }

    public int getRetcode() {
        return retcode;
    }

    public void setRetcode(int retcode) {
        this.retcode = retcode;
    }

}
