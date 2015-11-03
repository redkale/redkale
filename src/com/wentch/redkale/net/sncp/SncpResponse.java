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
        ByteBuffer buffer = context.pollBuffer();
        final int bodyLength = (out == null ? 0 : out.count());
        final int bufsize = buffer.capacity() - HEADER_SIZE;
        if (bufsize > bodyLength) { //只需一帧
            //---------------------head----------------------------------
            fillHeader(buffer, bodyLength, 0, bodyLength, retcode);
            //---------------------body----------------------------------
            out.toBuffer(buffer);
            buffer.flip();
            finish(buffer);
        } else {
            final int frames = (bodyLength / bufsize) + (bodyLength % bufsize > 0 ? 1 : 0);
            final ByteBuffer[] buffers = new ByteBuffer[frames];
            int pos = 0;
            for (int i = 0; i < frames; i++) {
                if (i != 0) buffer = context.pollBuffer();
                int len = Math.min(bufsize, bodyLength - pos);
                fillHeader(buffer, bodyLength, pos, len, retcode);
                buffers[i] = buffer;
                out.toBuffer(pos, buffer);
                pos += len;
                buffer.flip();
            }
            finish(buffers);
        }
    }

    private void fillHeader(ByteBuffer buffer, int bodyLength, int bodyOffset, int framelength, int retcode) {
        //---------------------head----------------------------------
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
    }
}
