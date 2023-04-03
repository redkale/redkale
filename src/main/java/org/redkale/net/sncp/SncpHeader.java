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

    public static final int HEADER_SIZE = 72;

    private static final byte[] EMPTY_ADDR = new byte[4];

    private Long seqid;

    private Uint128 serviceid;

    //【预留字段】service接口版本
    private int serviceVersion;

    private Uint128 actionid;

    //SncpRequest的值是clientSncpAddress，SncpResponse的值是serverSncpAddress
    private byte[] addrBytes;

    //响应方地址端口
    private int addrPort;

    // 预留扩展位
    private int abilities;

    //时间戳
    private long timestamp;

    //结果码，非0表示错误
    private int retcode;

    //body长度
    private int bodyLength;

    private boolean valid;

    public SncpHeader() {
    }

    public SncpHeader(InetSocketAddress clientSncpAddress, Uint128 serviceid, Uint128 actionid) {
        this.addrBytes = clientSncpAddress == null ? new byte[4] : clientSncpAddress.getAddress().getAddress();
        this.addrPort = clientSncpAddress == null ? 0 : clientSncpAddress.getPort();
        this.serviceid = serviceid;
        this.actionid = actionid;
        if (addrBytes.length != 4) {
            throw new SncpException("address bytes length must be 4, but " + addrBytes.length);
        }
    }

    //返回Header Size
    public int read(ByteBuffer buffer) {
        this.seqid = buffer.getLong();  //8
        int size = buffer.getChar();
        this.valid = size != HEADER_SIZE;   //2
        this.serviceid = Uint128.read(buffer); //16
        this.serviceVersion = buffer.getInt(); //4
        this.actionid = Uint128.read(buffer); //16
        if (this.addrBytes == null) {
            this.addrBytes = new byte[4];
        }
        buffer.get(this.addrBytes); //addr      4
        this.addrPort = buffer.getChar(); //port 2
        this.abilities = buffer.getInt(); //4
        this.timestamp = buffer.getLong(); //8
        this.retcode = buffer.getInt(); //4
        this.bodyLength = buffer.getInt(); //4
        return size;
    }

    //返回Header Size
    public int read(ByteArray array) {
        int offset = 0;
        this.seqid = array.getLong(offset);  //8
        offset += 8;
        int size = array.getChar(offset);
        this.valid = size != HEADER_SIZE;  //2
        offset += 2;
        this.serviceid = array.getUint128(offset); //16
        offset += 16;
        this.serviceVersion = array.getInt(offset); //4
        offset += 4;
        this.actionid = array.getUint128(offset); //16        
        offset += 16;
        this.addrBytes = array.getBytes(offset, 4); //addr 4        
        offset += 4;
        this.addrPort = array.getChar(offset); //port 2        
        offset += 2;
        this.abilities = array.getInt(offset); //4       
        offset += 4;
        this.timestamp = array.getLong(offset); //8        
        offset += 8;
        this.retcode = array.getInt(offset); //4          
        offset += 4;
        this.bodyLength = array.getInt(offset); //4 
        return size;
    }

    public ByteArray writeTo(ByteArray array, InetSocketAddress address, long newSeqid, int bodyLength, int retcode) {
        byte[] newAddrBytes = address == null ? EMPTY_ADDR : address.getAddress().getAddress();
        int newAddrPort = address == null ? 0 : address.getPort();
        return writeTo(array, newAddrBytes, newAddrPort, newSeqid, bodyLength, retcode);
    }

    public ByteArray writeTo(ByteArray array, byte[] newAddrBytes, int newAddrPort, long newSeqid, int bodyLength, int retcode) {
        if (newAddrBytes.length != 4) {
            throw new SncpException("address bytes length must be 4, but " + newAddrBytes.length);
        }
        if (array.length() < HEADER_SIZE) {
            throw new SncpException("ByteArray length must more " + HEADER_SIZE);
        }
        int offset = 0;
        array.putLong(offset, newSeqid); //8
        offset += 8;
        array.putChar(offset, (char) HEADER_SIZE); //2
        offset += 2;
        array.putUint128(offset, serviceid); //16
        offset += 16;
        array.putInt(offset, serviceVersion); //4
        offset += 4;
        array.putUint128(offset, actionid); //16   
        offset += 16;
        array.put(offset, newAddrBytes); //4   
        offset += 4;
        array.putChar(offset, (char) newAddrPort); //2      
        offset += 2;
        array.putInt(offset, abilities); //4 
        offset += 4;
        array.putLong(offset, System.currentTimeMillis()); //8 
        offset += 8;
        array.putInt(offset, retcode); //4 
        offset += 4;
        array.putInt(offset, bodyLength); //4   
        return array;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
            + (this.seqid == null ? ("{serviceid=" + this.serviceid) : ("{seqid=" + this.seqid + ",serviceid=" + this.serviceid))
            + ",serviceVersion=" + this.serviceVersion
            + ",actionid=" + this.actionid
            + ",address=" + getAddress()
            + ",timestamp=" + this.timestamp
            + ",retcode=" + this.retcode
            + ",bodyLength=" + this.bodyLength
            + "}";
    }

    public InetSocketAddress getAddress() {
        if (addrBytes == null) {
            return null;
        }
        return new InetSocketAddress((0xff & addrBytes[0]) + "." + (0xff & addrBytes[1]) + "." + (0xff & addrBytes[2]) + "." + (0xff & addrBytes[3]), addrPort);
    }

    public boolean isValid() {
        return valid;
    }

    //供client端request和response的header判断
    public boolean checkValid(SncpHeader other) {
        return Objects.equals(this.seqid, other.seqid)
            && Objects.equals(this.serviceid, other.serviceid)
            && Objects.equals(this.actionid, other.actionid);
    }

    public Long getSeqid() {
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

    public byte[] getAddrBytes() {
        return addrBytes;
    }

    public int getAddrPort() {
        return addrPort;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    public int getRetcode() {
        return retcode;
    }

}
