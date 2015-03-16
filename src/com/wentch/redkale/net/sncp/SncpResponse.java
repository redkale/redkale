/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.net.*;
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
        return new ObjectPool<>(creatCounter, cycleCounter, max, creator, (x) -> ((SncpResponse) x).recycle());
    }

    protected SncpResponse(Context context, SncpRequest request) {
        super(context, request);
    }

    public void finish(final int retcode, final byte[] bytes) {
        ByteBuffer buffer = context.pollBuffer();
        //---------------------head----------------------------------
        buffer.putLong(request.getSeqid());
        buffer.putChar((char) SncpRequest.HEADER_SIZE);
        buffer.putLong(request.getServiceid());
        buffer.putLong(request.getNameid());
        TwoLong actionid = request.getActionid();
        buffer.putLong(actionid.getFirst());
        buffer.putLong(actionid.getSecond());
        buffer.put((byte) 1); // frame count
        buffer.put((byte) 0); //frame index
        buffer.putInt(retcode);
        buffer.putInt((bytes == null ? 0 : bytes.length));
        //---------------------body----------------------------------
        if (bytes != null) buffer.put(bytes);
        buffer.flip();
        finish(buffer);
    }
}
