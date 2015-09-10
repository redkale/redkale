/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep.rtcp;

/**
 1byte = 8bits
 *
 * @author zhangjx
 */
public class RtcpHeader {

    public static final int RTCP_SR = 200;  //发送者报告 描述作为活跃发送者成员的发送和接收统计数字

    public static final int RTCP_RR = 201; //接收者报告  描述非活跃发送者成员的接收统计数字

    public static final int RTCP_SDES = 202; //源描述项。 其中包括规范名CNAME

    public static final int RTCP_BYE = 203;  //关闭 表明参与者将结束会话

    public static final int RTCP_APP = 204; //应用描述功能   

    /**
     * protocol version
     * 占2比特。 表示RTP 的版本号。
     */
    protected int version;

    /**
     * padding flag
     * 占1比特。 置“1”表示用户数据最后加有填充位，用户数据中最后一个字节是填充位计数，它表示一共加了多少个填充位。在两种情况下可能
     * 需要填充，一是某些加密算法要求数据块大小固定；二是在一个低层协议数据包中装载多个RTP 分组。
     */
    protected boolean padding; //

    /**
     * 占1比特。 置“1”表示RTP 报头后紧随一个扩展报头。
     */
    protected boolean extend;

    /**
     * varies by packet type
     */
    protected int count;

    /**
     * RTCP packet type
     */
    protected int packetType;   //占1个字节

    /**
     * Packet length in words, w/o this word
     */
    protected int length;

    protected RtcpHeader() {
        this(false, 0);
    }

    public RtcpHeader(boolean padding, int pt) {
        this.padding = padding;
        this.packetType = pt;
        this.count = 0;
        this.length = 0;
        this.version = 2;
    }

    protected int decode(byte[] rawData, int offSet) {
        int b = rawData[offSet++] & 0xff;

        this.version = (b & 0xC0) >> 6;
        this.padding = (b & 0x20) == 0x020;

        this.count = b & 0x1F;

        this.packetType = rawData[offSet++] & 0x000000FF;

        this.length |= rawData[offSet++] & 0xFF;
        this.length <<= 8;
        this.length |= rawData[offSet++] & 0xFF;

        /**
         * The length of this RTCP packet in 32-bit words minus one, including
         * the header and any padding. (The offset of one makes zero a valid
         * length and avoids a possible infinite loop in scanning a compound
         * RTCP packet, while counting 32-bit words avoids a validity check for
         * a multiple of 4.)
         */
        this.length = (this.length * 4) + 4;

        return offSet;
    }

    protected int encode(byte[] rawData, int offSet) {
        rawData[offSet] = (byte) (this.version << 6);
        if (this.padding) {
            rawData[offSet] = (byte) (rawData[offSet] | 0x20);
        }

        rawData[offSet] = (byte) (rawData[offSet] | (this.count & 0x1F));

        offSet++;

        rawData[offSet++] = (byte) (this.packetType & 0x000000FF);

        // Setting length is onus of concrete class. But we increment the offSet
        offSet += 2;

        return offSet;
    }

    public int getVersion() {
        return version;
    }

    public boolean isPadding() {
        return padding;
    }

    public int getCount() {
        return count;
    }

    public int getPacketType() {
        return packetType;
    }

    public int getLength() {
        return length;
    }
}
