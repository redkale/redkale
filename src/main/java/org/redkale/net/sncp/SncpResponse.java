/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.lang.reflect.Type;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.*;
import org.redkale.convert.bson.BsonWriter;
import org.redkale.net.Response;
import static org.redkale.net.sncp.SncpHeader.HEADER_SIZE;
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

    protected final BsonWriter writer = new BsonWriter();

    protected final CompletionHandler realHandler = new CompletionHandler() {
        @Override
        public void completed(Object result, Object attachment) {
            finish(paramHandlerResultType, result);
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            finishError(exc);
        }
    };

    protected Type paramHandlerResultType;

    protected CompletionHandler paramAsyncHandler;

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

    public SncpResponse paramAsyncHandler(Class<? extends CompletionHandler> paramHandlerType, Type paramHandlerResultType) {
        this.paramHandlerResultType = paramHandlerResultType;
        this.paramAsyncHandler = paramHandlerType == CompletionHandler.class ? realHandler : SncpAsyncHandler.createHandler(paramHandlerType, realHandler);
        return this;
    }

    public <T extends CompletionHandler> T getParamAsyncHandler() {
        return (T) this.paramAsyncHandler;
    }

    @Override
    protected void prepare() {
        super.prepare();
    }

    @Override
    protected boolean recycle() {
        writer.clear();
        this.paramHandlerResultType = null;
        this.paramAsyncHandler = null;
        return super.recycle();
    }

    public BsonWriter getBsonWriter() {
        return writer;
    }

    @Override
    protected ExecutorService getWorkExecutor() {
        return super.getWorkExecutor();
    }

    @Override
    protected ThreadHashExecutor getWorkHashExecutor() {
        return super.getWorkHashExecutor();
    }

    @Override
    protected void updateNonBlocking(boolean nonBlocking) {
        super.updateNonBlocking(nonBlocking);
    }

    @Override
    protected boolean inNonBlocking() {
        return super.inNonBlocking();
    }

    @Override
    protected void finishError(Throwable t) {
        finish(RETCODE_THROWEXCEPTION, null);
    }

    public final void finishVoid() {
        BsonWriter out = getBsonWriter();
        out.writePlaceholderTo(HEADER_SIZE);
        finish(0, out);
    }

    public final void finishFuture(final Type futureResultType, final CompletionStage future) {
        if (future == null) {
            finishVoid();
        } else {
            future.whenComplete((v, t) -> {
                if (t != null) {
                    finishError((Throwable) t);
                } else {
                    finish(futureResultType, v);
                }
            });
        }
    }

    public final void finish(final Type type, final Object result) {
        BsonWriter out = getBsonWriter();
        out.writePlaceholderTo(HEADER_SIZE);
        if (result != null || type != Void.class) {
            out.writeByte((byte) 0);  //body的第一个字节为0，表示返回结果对象，而不是参数回调对象
            context.getBsonConvert().convertTo(out, type, result);
        }
        finish(0, out);
    }

    //调用此方法时out已写入SncpHeader
    public void finish(final int retcode, final BsonWriter out) {
        if (out == null) {
            final ByteArray buffer = new ByteArray(HEADER_SIZE);
            fillHeader(buffer, 0, retcode);
            finish(buffer);
            return;
        }
        final ByteArray array = out.toByteArray();
        final int bodyLength = array.length() - HEADER_SIZE;
        fillHeader(array, bodyLength, retcode);
        finish(array);
    }

    protected void fillHeader(ByteArray buffer, int bodyLength, int retcode) {
        SncpHeader header = request.getHeader();
        header.writeTo(buffer, this.addrBytes, this.addrPort, header.getSeqid(), bodyLength, retcode);
    }

}
