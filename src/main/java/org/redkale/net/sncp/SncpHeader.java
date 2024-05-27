/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.redkale.util.*;

/** @author zhangjx */
public class SncpHeader {

    public static final int HEADER_SUBSIZE = 69;

    public static final byte KEEPALIVE_ON = 0;

    public static final byte KEEPALIVE_OFF = -1;

    private Long seqid;

    private Uint128 serviceid;

    String serviceName;

    // sncp协议版本
    private int sncpVersion;

    private Uint128 actionid;

    String methodName;

    // SncpRequest的值是clientSncpAddress，SncpResponse的值是serverSncpAddress
    private byte[] addrBytes;

    // 响应方地址端口
    private int addrPort;

    // 保持连接，0：keepAlive; -1:关闭连接
    private byte keepAlive;

    // 时间戳
    private long timestamp;

    // 日志ID
    private String traceid;

    // 结果码，非0表示错误
    private int retcode;

    // body长度
    private int bodyLength;

    private boolean valid;

    private SncpHeader() {}

    public static SncpHeader create(
            InetSocketAddress clientSncpAddress,
            Uint128 serviceid,
            String serviceName,
            Uint128 actionid,
            String methodName) {
        SncpHeader header = new SncpHeader();
        header.addrBytes = clientSncpAddress == null
                ? new byte[4]
                : clientSncpAddress.getAddress().getAddress();
        header.addrPort = clientSncpAddress == null ? 0 : clientSncpAddress.getPort();
        header.serviceid = serviceid;
        header.serviceName = serviceName;
        header.actionid = actionid;
        header.methodName = methodName;
        if (header.addrBytes.length != 4) {
            throw new SncpException("address bytes length must be 4, but " + header.addrBytes.length);
        }
        return header;
    }

    // 此处的buffer不包含开头headerSize的两字节，返回Header Size
    public static SncpHeader read(ByteBuffer buffer, final int headerSize) {
        SncpHeader header = new SncpHeader();
        header.valid = headerSize > HEADER_SUBSIZE; // 2
        header.sncpVersion = buffer.getInt(); // 4
        header.seqid = buffer.getLong(); // 8
        header.serviceid = Uint128.read(buffer); // 16
        header.actionid = Uint128.read(buffer); // 16
        if (header.addrBytes == null) {
            header.addrBytes = new byte[4];
        }
        buffer.get(header.addrBytes); // addr      4
        header.addrPort = buffer.getChar(); // port 2
        header.keepAlive = buffer.get(); // 1
        header.timestamp = buffer.getLong(); // 8
        int traceSize = buffer.getChar(); // 2
        if (traceSize > 0) {
            byte[] traces = new byte[traceSize];
            buffer.get(traces);
            header.traceid = new String(traces, StandardCharsets.UTF_8);
        }
        header.retcode = buffer.getInt(); // 4
        header.bodyLength = buffer.getInt(); // 4
        return header;
    }

    // 此处的array不包含开头headerSize的两字节，返回Header Size
    public static SncpHeader read(ByteArray array, final int headerSize) {
        SncpHeader header = new SncpHeader();
        header.valid = headerSize > HEADER_SUBSIZE; // 2
        int offset = 0;
        header.sncpVersion = array.getInt(offset); // 4
        offset += 4;
        header.seqid = array.getLong(offset); // 8
        offset += 8;
        header.serviceid = array.getUint128(offset); // 16
        offset += 16;
        header.actionid = array.getUint128(offset); // 16
        offset += 16;
        header.addrBytes = array.getBytes(offset, 4); // addr 4
        offset += 4;
        header.addrPort = array.getChar(offset); // port 2
        offset += 2;
        header.keepAlive = array.get(offset); // 1
        offset += 1;
        header.timestamp = array.getLong(offset); // 8
        offset += 8;
        int traceSize = array.getChar(offset); // 2
        offset += 2;
        if (traceSize > 0) {
            byte[] traces = array.getBytes(offset, traceSize);
            header.traceid = new String(traces, StandardCharsets.UTF_8);
            offset += traceSize;
        }
        header.retcode = array.getInt(offset); // 4
        offset += 4;
        header.bodyLength = array.getInt(offset); // 4
        return header;
    }

    public ByteArray writeTo(
            ByteArray array, SncpClientRequest clientRequest, byte keepAlive, int bodyLength, int retcode) {
        return writeTo(
                array,
                this.addrBytes,
                this.addrPort,
                (Long) clientRequest.getRequestid(),
                clientRequest.traceBytes(),
                keepAlive,
                bodyLength,
                retcode);
    }

    public ByteArray writeTo(ByteArray array, SncpResponse response, byte keepAlive, int bodyLength, int retcode) {
        SncpRequest request = response.request();
        return writeTo(
                array,
                response.addrBytes,
                response.addrPort,
                (Long) request.getRequestid(),
                request.traceBytes(),
                keepAlive,
                bodyLength,
                retcode);
    }

    private ByteArray writeTo(
            ByteArray array,
            byte[] newAddrBytes,
            int newAddrPort,
            long newSeqid,
            byte[] traces,
            byte keepAlive,
            int bodyLength,
            int retcode) {
        if (newAddrBytes.length != 4) {
            throw new SncpException("address bytes length must be 4, but " + newAddrBytes.length);
        }
        if (traces == null) {
            traces = SncpClientRequest.EMPTY_TRACEID;
        }
        int size = HEADER_SUBSIZE + 2 + traces.length;
        if (array.length() < size) {
            throw new SncpException("ByteArray length must more " + size);
        }
        int offset = 0;
        array.putChar(offset, (char) size); // 2
        offset += 2;
        array.putInt(offset, sncpVersion); // 4
        offset += 4;
        array.putLong(offset, newSeqid); // 8
        offset += 8;
        array.putUint128(offset, serviceid); // 16
        offset += 16;
        array.putUint128(offset, actionid); // 16
        offset += 16;
        array.put(offset, newAddrBytes); // 4
        offset += 4;
        array.putChar(offset, (char) newAddrPort); // 2
        offset += 2;
        array.put(offset, keepAlive); // 1
        offset += 1;
        array.putLong(offset, System.currentTimeMillis()); // 8
        offset += 8;
        array.putChar(offset, (char) traces.length); // 2
        offset += 2;
        if (traces.length > 0) {
            array.put(offset, traces); // traces.length
            offset += traces.length;
        }
        array.putInt(offset, retcode); // 4
        offset += 4;
        array.putInt(offset, bodyLength); // 4
        return array;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + (this.seqid == null
                        ? ("{serviceid=" + this.serviceid + ",serviceName=" + this.serviceName)
                        : ("{seqid=" + this.seqid + ",serviceid=" + this.serviceid + ",serviceName="
                                + this.serviceName))
                + ",sncpVersion=" + this.sncpVersion
                + ",actionid=" + this.actionid
                + ",methodName=" + this.methodName
                + ",address=" + getAddress()
                + ",keepAlive=" + isKeepAlive()
                + ",timestamp=" + this.timestamp
                + ",traceid=" + getTraceid()
                + ",retcode=" + this.retcode
                + ",bodyLength=" + this.bodyLength
                + "}";
    }

    public InetSocketAddress getAddress() {
        return addrBytes == null
                ? null
                : new InetSocketAddress(
                        (0xff & addrBytes[0]) + "." + (0xff & addrBytes[1]) + "." + (0xff & addrBytes[2]) + "."
                                + (0xff & addrBytes[3]),
                        addrPort);
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isKeepAlive() {
        return keepAlive != -1;
    }

    // 供client端request和response的header判断
    public boolean checkValid(SncpHeader other) {
        return Objects.equals(this.serviceid, other.serviceid) && Objects.equals(this.actionid, other.actionid);
    }

    public static int calcHeaderSize(SncpClientRequest request) {
        return HEADER_SUBSIZE + 2 + request.traceBytes().length;
    }

    public static int calcHeaderSize(SncpRequest request) {
        return HEADER_SUBSIZE + 2 + request.traceBytes().length;
    }

    public Long getSeqid() {
        return seqid;
    }

    public Uint128 getServiceid() {
        return serviceid;
    }

    public int getSncpVersion() {
        return sncpVersion;
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

    public long getTimestamp() {
        return timestamp;
    }

    public String getTraceid() {
        return traceid;
    }

    public int getRetcode() {
        return retcode;
    }

    public int getBodyLength() {
        return bodyLength;
    }
}
