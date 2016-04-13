/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import static org.redkale.net.sncp.SncpRequest.HEADER_SIZE;
import java.nio.*;
import java.util.concurrent.atomic.*;
import org.redkale.convert.bson.*;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
public final class SncpResponse extends Response<SncpContext, SncpRequest> {

    public static final int RETCODE_ILLSERVICEID = (1 << 10); //无效serviceid

    public static final int RETCODE_ILLACTIONID = (1 << 11); //无效actionid

    public static final int RETCODE_THROWEXCEPTION = (1 << 30); //内部异常

    public static ObjectPool<Response> createPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<Response> creator) {
        return new ObjectPool<>(creatCounter, cycleCounter, max, creator, (x) -> ((SncpResponse) x).prepare(), (x) -> ((SncpResponse) x).recycle());
    }

    private final byte[] addrBytes;

    private final int addrPort;

    public static String getRetCodeInfo(int retcode) {
        if (retcode == RETCODE_ILLSERVICEID) return "serviceid is invalid";
        if (retcode == RETCODE_ILLACTIONID) return "actionid is invalid";
        if (retcode == RETCODE_THROWEXCEPTION) return "Inner exception";
        return null;
    }

    protected SncpResponse(SncpContext context, SncpRequest request) {
        super(context, request);
        this.addrBytes = context.getServerAddress().getAddress().getAddress();
        this.addrPort = context.getServerAddress().getPort();
    }

    public void finish(final int retcode, final BsonWriter out) {
        if (out == null) {
            final ByteBuffer buffer = context.pollBuffer();
            fillHeader(buffer, 0, retcode);
            finish(buffer);
            return;
        }
        final int respBodyLength = out.count(); //body总长度
        final ByteBuffer[] buffers = out.toBuffers();
        fillHeader(buffers[0], respBodyLength - HEADER_SIZE, retcode);
        finish(buffers);
    }

    private void fillHeader(ByteBuffer buffer, int bodyLength, int retcode) {
        //---------------------head----------------------------------
        final int currentpos = buffer.position();
        buffer.position(0);
        buffer.putLong(request.getSeqid());
        buffer.putChar((char) SncpRequest.HEADER_SIZE);
        DLong.write(buffer, request.getServiceid());
        DLong.write(buffer, request.getActionid());
        buffer.put(addrBytes);
        buffer.putChar((char) this.addrPort);
        buffer.putInt(bodyLength);
        buffer.putInt(retcode);
        buffer.position(currentpos);
    }
}
