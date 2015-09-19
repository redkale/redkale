/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep.rtp;

import com.wentch.redkale.net.icep.*;
import java.nio.*;

/**
 * The RTP header has the following format:
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * |            contributing source (CSRC) identifiers             |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 
 * The first twelve octets are present in every RTP packet, while the
 * list of CSRC identifiers is present only when inserted by a mixer.
 * 
 * The version defined by RFC3550 specification is two.
 *
 * @author zhangjx
 */
public class RtpHeader implements IcepCoder<RtpHeader> {

    /**
     * 该值占4个字节， 由7种数据组成，按位存储
     *  version(2bits) + padding(1bit) + extend(1bit) + crscs(4bits) + marker(1bit) + payloadtype(7bits) + sn(16bits)
     * version :     占2比特。 表示RTP 的版本号  0b10。
     * padding :     占1比特。 置“1”表示用户数据最后加有填充位，用户数据中最后一个字节是填充位计数，它表示一共加了多少个填充位。
     *                         在两种情况下可能需要填充: 一是某些加密算法要求数据块大小固定；二是在一个低层协议数据包中装载多个RTP 分组。
     * extend :      占1比特。 置“1”表示RTP 报头后紧随一个扩展报头。
     * crscs :       占4比特。 CSRC计数包括紧接在固定头后CSRC标识符个数。
     * marker :      占1比特。 标记解释由设置定义，目的在于允许重要事件在包流中标记出来。设置可定义其他标示位，或通过改变位数量来指定没有标记位。
     * payloadtype : 占7比特。 载荷类型: 记录后面资料使用哪种 Codec ， receiver 端找出相应的 decoder 解码出來。
     * sn :         占16比特。 每发送一个RTP数据包该序号增加1。该序号在接收方可用来发现丢失的数据包和对到来的数据包进行排序。 初值为随机数，每发送一个增加1。可供接收方检测分组丢失和恢复分组次序。
     * 
     */
    private int headval = 0b10_0_0_0000_0_0000000_0000000000000000;

    /**
     * 占4个字节。 时间戳 表示RTP分组第一个字节的取样时刻。其初值为随机数，每个采用周期加1。如果每次传送20ms的数据，由于音频的采样频率为8000Hz，即每20ms有160次采样，则每传送20ms的数据，时戳增加160。
     */
    private int timestamp;

    /**
     * 同步源标识符（SSRC） 相当于每个数据流的唯一ID
     * 占4字节。 用来标识一个同步源。此标识符是随机选择的，但要保证同一RTP会话中的任意两个SSRC各不相同，RTP必须检测并解决冲突。
     */
    private int ssrc;

    /**
     * 提供源标识符（CSRC）
     * 占0-60个字节。 它可有0~15项标识符，每一项长度固定为32比特，其项数由CC字段（crscs）来确定。如果提供源多于15个，则只有15个被标识。
     */
    private int[] csrc;

    /**
     * 占2字节
     */
    private short exttype;

    /**
     * 扩展数据
     * 占4*N字节， 必须是4字节的倍数
     */
    private byte[] extdata;

    @Override
    public RtpHeader decode(final ByteBuffer buffer) {
        this.headval = buffer.getInt();
        this.timestamp = buffer.getInt();
        this.ssrc = buffer.getInt();
        final int csrcs = getCrscs();
        if (csrcs > 0) {
            this.csrc = new int[csrcs];
            for (int i = 0; i < csrcs; i++) {
                this.csrc[i] = buffer.getInt();
            }
        }
        if (isExtend()) {
            this.exttype = buffer.getShort();
            int extdatalen = (buffer.getShort() & 0xffff) * 4;
            this.extdata = new byte[extdatalen];
            buffer.get(extdata);
        }
        return this;
    }

    @Override
    public ByteBuffer encode(final ByteBuffer buffer) {
        buffer.putInt(this.headval);
        buffer.putInt(this.timestamp);
        buffer.putInt(this.ssrc);
        final int csrcs = getCrscs();
        if (csrcs > 0) {
            for (int i = 0; i < csrcs; i++) {
                buffer.putInt(this.csrc[i]);
            }
        }
        if (isExtend()) {
            buffer.putShort(this.exttype);
            buffer.putShort((short) (this.extdata.length / 4));
            buffer.put(this.extdata);
        }
        return buffer;
    }

    public static int getSsrc(final ByteBuffer buffer) {
        return buffer.getInt(8);
    }

    public int getVersion() {
        return (headval >> 30) & 0b11;
    }

    public boolean isPadding() {
        return (headval & 0b00_1_0_0000_0_0000000_0000000000000000) > 0;
    }

    public void setPadding(boolean padding) {
        if (padding) {
            headval |= 0b00_1_0_0000_0_0000000_0000000000000000;
        } else {
            headval &= 0b11_0_1_1111_1_1111111_1111111111111111;
        }
    }

    public boolean isExtend() {
        return (headval & 0b00_0_1_0000_0_0000000_0000000000000000) > 0;
    }

    public short getExttype() {
        return exttype;
    }

    public void setExttype(short exttype) {
        this.exttype = exttype;
    }

    public int[] getCsrc() {
        return csrc;
    }

    public void setCsrc(int[] csrc) {
        this.csrc = csrc != null && csrc.length > 0 ? csrc : null;
        if (this.csrc != null) {
            this.headval = (headval & 0b11_1_1_0000_1_1111111_1111111111111111) | ((csrc.length << 24) & 0b00_0_0_1111_0_0000000_0000000000000000);
        } else {
            this.headval &= 0b11_1_1_0000_1_1111111_1111111111111111;
        }
    }

    public byte[] getExtdata() {
        return extdata;
    }

    public void setExtdata(byte[] exdata) {
        boolean extend = exdata != null || exdata.length > 0;
        if (extend) {
            if ((exdata.length & 0b100) > 0) throw new RuntimeException("extdata length(" + exdata.length + ") is illegal");
            headval |= 0b00_0_1_0000_0_0000000_0000000000000000;
        } else {
            headval &= 0b11_1_0_1111_1_1111111_1111111111111111;
        }
        this.extdata = (exdata != null && exdata.length > 0) ? exdata : null;
    }

    public int getCrscs() {
        return (headval >> 24) & 0b1111;
    }

    public boolean isMarker() {
        return (headval & 0b00_0_0_0000_1_0000000_0000000000000000) > 0;
    }

    public int getPayloadtype() {
        return (headval >> 16) & 0b1111111;
    }

    public void setPayloadtype(int payloadtype) {
        headval = (headval & 0b11_1_1_1111_1_0000000_1111111111111111) | ((payloadtype << 16) & 0b00_0_0_0000_0_1111111_0000000000000000);
    }

    public int getSeqnumber() {
        return headval & 0xFFFF;
    }

    public void setSeqnumber(int seqnumber) {
        headval = (headval >> 16 << 16) | (seqnumber & 0x0000FFFF);
    }

}
