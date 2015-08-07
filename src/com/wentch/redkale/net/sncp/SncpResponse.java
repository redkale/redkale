/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

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

    public void finish(final int retcode, final byte[] bytes) {
        ByteBuffer buffer = context.pollBuffer();
        final int bodyLength = (bytes == null ? 0 : bytes.length);
        final int patch = bodyLength / (buffer.capacity() - HEADER_SIZE) + (bodyLength % (buffer.capacity() - HEADER_SIZE) > 0 ? 1 : 0);
        if (patch <= 1) {
            //---------------------head----------------------------------
            fillHeader(buffer, retcode, 1, 0, bodyLength);
            //---------------------body----------------------------------
            if (bytes != null) buffer.put(bytes);
            buffer.flip();
            finish(buffer);
        } else {
            final ByteBuffer[] buffers = new ByteBuffer[patch];
            int pos = 0;
            for (int i = patch - 1; i >= 0; i--) {
                if (i != patch - 1) buffer = context.pollBuffer();
                fillHeader(buffer, retcode, patch, i, bodyLength);
                buffers[i] = buffer;
                int len = Math.min(buffer.remaining(), bytes.length - pos);
                buffer.put(bytes, pos, len);
                pos += len;
                buffer.flip();
            }
            finish(buffers);
        }
    }

    private void fillHeader(ByteBuffer buffer, int retcode, int frameCount, int frameIndex, int bodyLength) {
        //---------------------head----------------------------------
        buffer.putLong(request.getSeqid());
        buffer.putChar((char) SncpRequest.HEADER_SIZE);
        buffer.putLong(request.getServiceid());
        buffer.putLong(request.getNameid());
        DLong actionid = request.getActionid();
        buffer.putLong(actionid.getFirst());
        buffer.putLong(actionid.getSecond());
        buffer.put(addrBytes);
        buffer.putInt(this.addrPort);
        buffer.put((byte) frameCount); // frame count
        buffer.put((byte) frameIndex); //frame index
        buffer.putInt(retcode);
        buffer.putInt(bodyLength);
    }
}
