/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.nio.ByteBuffer;
import org.redkale.convert.bson.BsonWriter;
import org.redkale.net.Response;
import static org.redkale.net.sncp.SncpRequest.HEADER_SIZE;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpResponse extends Response<SncpContext, SncpRequest> {

    public static final int RETCODE_ILLSERVICEID = (1 << 1); //无效serviceid

    public static final int RETCODE_ILLSERVICEVER = (1 << 2); //无效serviceVersion

    public static final int RETCODE_ILLACTIONID = (1 << 3); //无效actionid

    public static final int RETCODE_THROWEXCEPTION = (1 << 4); //内部异常

    private final byte[] addrBytes;

    private final int addrPort;

    public static String getRetCodeInfo(int retcode) {
        if (retcode == RETCODE_ILLSERVICEID) {
            return "The serviceid is invalid";
        }
        if (retcode == RETCODE_ILLSERVICEVER) {
            return "The serviceVersion is invalid";
        }
        if (retcode == RETCODE_ILLACTIONID) {
            return "The actionid is invalid";
        }
        if (retcode == RETCODE_THROWEXCEPTION) {
            return "Inner exception";
        }
        return null;
    }

    protected SncpResponse(SncpContext context, SncpRequest request) {
        super(context, request);
        this.addrBytes = context.getServerAddress().getAddress().getAddress();
        this.addrPort = context.getServerAddress().getPort();
        if (this.addrBytes.length != 4) {
            throw new RuntimeException("SNCP serverAddress only support IPv4");
        }
    }

    @Override
    protected void prepare() {
        super.prepare();
    }

    @Override
    protected boolean recycle() {
        return super.recycle();
    }

    @Override
    protected void finish(boolean kill, ByteBuffer buffer) {
        super.finish(kill, buffer);
    }

    public void finish(final int retcode, final BsonWriter out) {
        if (out == null) {
            final ByteArray buffer = new ByteArray(SncpRequest.HEADER_SIZE);
            fillHeader(buffer, 0, retcode);
            finish(buffer);
            return;
        }
        final int respBodyLength = out.count(); //body总长度
        final ByteArray array = out.toByteArray();
        fillHeader(array, respBodyLength - HEADER_SIZE, retcode);
        finish(array);
    }

    protected void fillHeader(ByteArray buffer, int bodyLength, int retcode) {
        fillRespHeader(buffer, request.getSeqid(), request.getServiceid(), request.getServiceVersion(),
            request.getActionid(), request.getTraceid(), this.addrBytes, this.addrPort, bodyLength, retcode);
    }

    protected static void fillRespHeader(ByteArray buffer, long seqid, Uint128 serviceid, int serviceVersion,
        Uint128 actionid, String traceid, byte[] addrBytes, int addrPort, int bodyLength, int retcode) {
        //---------------------head----------------------------------
        int offset = 0;
        buffer.putLong(offset, seqid);
        offset += 8;
        buffer.putChar(offset, (char) SncpRequest.HEADER_SIZE);
        offset += 2;
        Uint128.write(buffer, offset, serviceid);
        offset += 16;
        buffer.putInt(offset, serviceVersion);
        offset += 4;
        Uint128.write(buffer, offset, actionid);
        offset += 16;
        buffer.put(offset, addrBytes);
        offset += addrBytes.length; //4
        buffer.putChar(offset, (char) addrPort);
        offset += 2;
        buffer.putInt(offset, bodyLength);
        offset += 4;
        buffer.putInt(offset, retcode);
        offset += 4;
    }

}
