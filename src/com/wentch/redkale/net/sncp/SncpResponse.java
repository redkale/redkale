/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.net.*;
import static com.wentch.redkale.net.sncp.SncpRequest.HEADER_SIZE;
import com.wentch.redkale.util.*;
import java.nio.*;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public final class SncpResponse extends Response<SncpRequest> {

    public static final int RETCODE_ILLSERVICEID = 10001; //无效serviceid

    public static final int RETCODE_ILLNAMEID = 10002; //无效nameid

    public static final int RETCODE_ILLACTIONID = 10003; //无效actionid

    public static final int RETCODE_THROWEXCEPTION = 10011; //内部异常

    public static ObjectPool<Response> createPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<Response> creator) {
        return new ObjectPool<>(creatCounter, cycleCounter, max, creator, (x) -> ((SncpResponse) x).prepare(), (x) -> ((SncpResponse) x).recycle());
    }

    private final byte[] addrBytes;

    private final int addrPort;

    public static String getRetCodeInfo(int retcode) {
        if (retcode == RETCODE_ILLSERVICEID) return "serviceid is invalid";
        if (retcode == RETCODE_ILLNAMEID) return "nameid is invalid";
        if (retcode == RETCODE_ILLACTIONID) return "actionid is invalid";
        if (retcode == RETCODE_THROWEXCEPTION) return "Inner exception";
        return null;
    }

    protected SncpResponse(Context context, SncpRequest request) {
        super(context, request);
        this.addrBytes = context.getServerAddress().getAddress().getAddress();
        this.addrPort = context.getServerAddress().getPort();
    }

    public void finish(final int retcode, final BsonWriter out) {
        if (out == null) {
            final ByteBuffer buffer = context.pollBuffer();
            fillHeader(buffer, 0, 0, 0, retcode);
            finish(buffer);
            return;
        }
        final int respBodyLength = out.count(); //body总长度
        final ByteBuffer[] buffers = out.toBuffers();
        if (this.channel.isTCP()) {  //TCP模式 或者 一帧数据     TCP的总长度需要减去第一个buffer的header长度       
            fillHeader(buffers[0], respBodyLength - HEADER_SIZE, 0, respBodyLength - HEADER_SIZE, retcode);
        } else {
            int pos = 0;
            for (ByteBuffer buffer : buffers) {
                int len = buffer.remaining() - HEADER_SIZE;
                fillHeader(buffer, respBodyLength, pos, len, retcode);
                pos += len;
            }
        }
        finish(buffers);
    }

    private void fillHeader(ByteBuffer buffer, int bodyLength, int bodyOffset, int framelength, int retcode) {
        //---------------------head----------------------------------
        final int currentpos = buffer.position();
        buffer.position(0);
        buffer.putLong(request.getSeqid());
        buffer.putChar((char) SncpRequest.HEADER_SIZE);
        buffer.putLong(request.getServiceid());
        buffer.putLong(request.getNameid());
        DLong actionid = request.getActionid();
        buffer.putLong(actionid.getFirst());
        buffer.putLong(actionid.getSecond());
        buffer.put(addrBytes);
        buffer.putChar((char) this.addrPort);
        buffer.putInt(bodyLength);
        buffer.putInt(bodyOffset);
        buffer.putInt(framelength);
        buffer.putInt(retcode);
        buffer.position(currentpos);
    }
}
