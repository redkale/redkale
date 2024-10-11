/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.lang.reflect.Type;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.*;
import org.redkale.annotation.ClassDepends;
import org.redkale.convert.pb.ProtobufBytesWriter;
import org.redkale.convert.pb.ProtobufWriter;
import org.redkale.net.Response;
import static org.redkale.net.sncp.SncpHeader.KEEPALIVE_OFF;
import static org.redkale.net.sncp.SncpHeader.KEEPALIVE_ON;
import org.redkale.util.ByteArray;
import org.redkale.util.Traces;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpResponse extends Response<SncpContext, SncpRequest> {

    public static final int RETCODE_ILLSERVICEID = (1 << 1); // 无效serviceid

    public static final int RETCODE_ILLSERVICEVER = (1 << 2); // 无效serviceVersion

    public static final int RETCODE_ILLACTIONID = (1 << 3); // 无效actionid

    public static final int RETCODE_THROWEXCEPTION = (1 << 4); // 内部异常

    final byte[] addrBytes;

    final int addrPort;

    protected final ProtobufWriter writer = new ProtobufBytesWriter();

    protected final CompletionHandler realHandler = new CompletionHandler() {
        @Override
        public void completed(Object result, Object attachment) {
            finish(paramHandlerType, result);
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            finishError(exc);
        }
    };

    protected Type paramHandlerType;

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

    public SncpResponse paramAsyncHandler(
            Class<? extends CompletionHandler> paramHandlerClass, Type paramHandlerType) {
        this.paramHandlerType = paramHandlerType;
        this.paramAsyncHandler = paramHandlerClass == CompletionHandler.class
                ? realHandler
                : SncpAsyncHandler.createHandler(paramHandlerClass, realHandler);
        return this;
    }

    @ClassDepends
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
        this.paramHandlerType = null;
        this.paramAsyncHandler = null;
        return super.recycle();
    }

    public ProtobufWriter getWriter() {
        return writer;
    }

    protected SncpRequest request() {
        return request;
    }

    protected void writeHeader(ByteArray array, int bodyLength, int retcode) {
        request.getHeader()
                .writeTo(array, this, request.isKeepAlive() ? KEEPALIVE_ON : KEEPALIVE_OFF, bodyLength, retcode);
    }

    @Override
    protected ExecutorService getWorkExecutor() {
        return super.getWorkExecutor();
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
    protected void defaultError(Throwable t) {
        finish(RETCODE_THROWEXCEPTION, null);
    }

    @ClassDepends
    public final void finishVoid() {
        int headerSize = SncpHeader.calcHeaderSize(request);
        ProtobufWriter out = getWriter();
        ((ProtobufBytesWriter) out).writePlaceholderTo(headerSize);
        finish(0, out);
    }

    @ClassDepends
    public final void finishFuture(final Type futureResultType, final Future future) {
        if (future == null) {
            finishVoid();
        } else if (future instanceof CompletionStage) {
            ((CompletionStage) future).whenComplete((v, t) -> {
                Traces.currentTraceid(request.getTraceid());
                if (t != null) {
                    finishError((Throwable) t);
                } else {
                    finish(futureResultType, v);
                }
                Traces.removeTraceid();
            });
        } else {
            try {
                finish(futureResultType, future.get());
            } catch (Exception e) {
                finishError(e);
            }
        }
    }

    public final void finish(final Type type, final Object result) {
        int headerSize = SncpHeader.calcHeaderSize(request);
        ProtobufWriter out = getWriter();
        ((ProtobufBytesWriter) out).writePlaceholderTo(headerSize);
        if (result != null || type != Void.class) {
            out.writeByte((byte) 0); // body的第一个字节为0，表示返回结果对象，而不是参数回调对象
            context.getProtobufConvert().convertTo(out, type, result);
        }
        finish(0, out);
    }

    // 调用此方法时out已写入SncpHeader的占位空间
    public void finish(final int retcode, final ProtobufWriter out) {
        int headerSize = SncpHeader.calcHeaderSize(request);
        if (out == null) {
            final ByteArray array = new ByteArray(headerSize).putPlaceholder(headerSize);
            writeHeader(array, 0, retcode);
            finish(array);
            return;
        }
        final ByteArray array = out.toByteArray();
        final int bodyLength = array.length() - headerSize;
        writeHeader(array, bodyLength, retcode);
        finish(array);
    }
}
