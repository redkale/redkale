/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class SncpHeader {

    public static final int HEADER_SIZE = 60;

    private static final byte[] EMPTY_ADDR = new byte[4];

    private long seqid;

    private Uint128 serviceid;

    private int serviceVersion;

    private Uint128 actionid;

    //SncpRequest的值是clientSncpAddress，SncpResponse的值是serverSncpAddress
    private byte[] addrBytes;

    private int addrPort;

    private int bodyLength;

    private int retcode;

    private long timestamp; //待加入 + 8

    private boolean valid;

    public SncpHeader() {
    }

    public SncpHeader(InetSocketAddress clientSncpAddress) {
        this.addrBytes = clientSncpAddress == null ? new byte[4] : clientSncpAddress.getAddress().getAddress();
        this.addrPort = clientSncpAddress == null ? 0 : clientSncpAddress.getPort();
    }

    public SncpHeader(InetSocketAddress clientSncpAddress, Uint128 serviceid, Uint128 actionid) {
        this.addrBytes = clientSncpAddress == null ? new byte[4] : clientSncpAddress.getAddress().getAddress();
        this.addrPort = clientSncpAddress == null ? 0 : clientSncpAddress.getPort();
        this.serviceid = serviceid;
        this.actionid = actionid;
    }

    public boolean read(ByteBuffer buffer) {
        this.seqid = buffer.getLong();  //8
        this.valid = buffer.getChar() != HEADER_SIZE;   //2
        this.serviceid = Uint128.read(buffer); //16
        this.serviceVersion = buffer.getInt(); //4
        this.actionid = Uint128.read(buffer); //16
        this.addrBytes = new byte[4];
        buffer.get(this.addrBytes); //addr      4
        this.addrPort = buffer.getChar(); //port 2
        this.bodyLength = buffer.getInt(); //4
        this.retcode = buffer.getInt(); //4
        return this.valid;
    }

    public boolean read(ByteArray array) {
        int offset = 0;
        this.seqid = array.getLong(offset);  //8
        offset += 8;
        this.valid = array.getChar(offset) != HEADER_SIZE;  //2
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
        return this.valid;
    }

    public ByteArray write(ByteArray array, InetSocketAddress address, long newSeqid, int bodyLength, int retcode) {
        byte[] newAddrBytes = address == null ? EMPTY_ADDR : address.getAddress().getAddress();
        int newAddrPort = address == null ? 0 : address.getPort();
        return write(array, newAddrBytes, newAddrPort, newSeqid, bodyLength, retcode);
    }

    public ByteArray write(ByteArray array, byte[] newAddrBytes, int newAddrPort, long newSeqid, int bodyLength, int retcode) {
        int offset = 0;
        array.putLong(offset, newSeqid);
        offset += 8;
        array.putChar(offset, (char) HEADER_SIZE);
        offset += 2;
        array.putUint128(offset, serviceid);
        offset += 16;
        array.putInt(offset, serviceVersion);
        offset += 4;
        array.putUint128(offset, actionid);
        offset += 16;
        array.put(offset, newAddrBytes);
        offset += newAddrBytes.length; //4
        array.putChar(offset, (char) newAddrPort);
        offset += 2;
        array.putInt(offset, bodyLength);
        offset += 4;
        array.putInt(offset, retcode); //4
        return array;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
            + "{seqid=" + this.seqid
            + ",serviceid=" + this.serviceid
            + ",serviceVersion=" + this.serviceVersion
            + ",actionid=" + this.actionid
            + ",address=" + getAddress()
            + ",bodyLength=" + this.bodyLength
            + ",retcode=" + this.retcode
            + "}";
    }

    public InetSocketAddress getAddress() {
        if (addrBytes == null || addrBytes[0] == 0) {
            return null;
        }
        return new InetSocketAddress((0xff & addrBytes[0]) + "." + (0xff & addrBytes[1]) + "." + (0xff & addrBytes[2]) + "." + (0xff & addrBytes[3]), addrPort);
    }

    public boolean isValid() {
        return valid;
    }

    //供client端request和response的header判断
    public boolean checkValid(SncpHeader other) {
        return this.seqid == other.seqid && Objects.equals(this.serviceid, other.serviceid) && Objects.equals(this.actionid, other.actionid);
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

    public int getRetcode() {
        return retcode;
    }

    public void setRetcode(int retcode) {
        this.retcode = retcode;
    }

}
