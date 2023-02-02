/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import org.redkale.convert.bson.BsonWriter;
import org.redkale.net.Response;
import static org.redkale.net.sncp.SncpHeader.HEADER_SIZE;
import org.redkale.util.ByteArray;

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
            throw new SncpException("SNCP serverAddress only support IPv4");
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

    public void finish(final int retcode, final BsonWriter out) {
        if (out == null) {
            final ByteArray buffer = new ByteArray(HEADER_SIZE);
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
        SncpHeader header = request.getHeader();
        header.write(buffer, this.addrBytes, this.addrPort, header.getSeqid(), bodyLength, retcode);
    }

}
