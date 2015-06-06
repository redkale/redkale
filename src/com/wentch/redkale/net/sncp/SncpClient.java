/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.net.*;
import static com.wentch.redkale.net.sncp.SncpRequest.HEADER_SIZE;
import com.wentch.redkale.service.*;
import com.wentch.redkale.util.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public final class SncpClient {

    private final Logger logger = Logger.getLogger(SncpClient.class.getSimpleName());

    private final boolean debug = logger.isLoggable(Level.FINEST);

    protected static final class SncpAction {

        protected final DLong actionid;

        protected final Method method;

        protected final Type resultTypes;  //void 必须设为 null

        protected final Type[] paramTypes;

        protected final boolean async;

        public SncpAction(Method method, DLong actionid) {
            this.actionid = actionid;
            Type rt = method.getGenericReturnType();
            if (rt instanceof TypeVariable) {
                TypeVariable tv = (TypeVariable) rt;
                if (tv.getBounds().length == 1) rt = tv.getBounds()[0];
            }
            this.resultTypes = rt == void.class ? null : rt;
            this.paramTypes = method.getGenericParameterTypes();
            this.method = method;
            this.async = false;// method.getReturnType() == void.class && method.getAnnotation(Async.class) != null;
        }

        @Override
        public String toString() {
            return "{" + actionid + "," + (method == null ? "null" : method.getName()) + "}";
        }
    }

    protected final long nameid;

    protected final long serviceid;

    protected final SncpAction[] actions;

    public SncpClient(final String serviceName, final Class serviceClass) {
        if (serviceName.length() > 10) throw new RuntimeException(serviceClass + " @Resource name(" + serviceName + ") too long , must less 11");
        this.nameid = Sncp.hash(MultiService.class.isAssignableFrom(serviceClass) ? serviceName : "");
        this.serviceid = Sncp.hash(serviceClass);
        final List<SncpAction> methodens = new ArrayList<>();
        //------------------------------------------------------------------------------
        Set<DLong> actionids = new HashSet<>();
        for (java.lang.reflect.Method method : serviceClass.getDeclaredMethods()) {
            if (method.isSynthetic()) continue;
            final int mod = method.getModifiers();
            if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) continue;
            if (Modifier.isStatic(mod)) continue;
            if (Modifier.isFinal(mod)) continue;
            if (method.getName().equals("getClass") || method.getName().equals("toString")) continue;
            if (method.getName().equals("equals") || method.getName().equals("hashCode")) continue;
            if (method.getName().equals("notify") || method.getName().equals("notifyAll") || method.getName().equals("wait")) continue;
            if (method.getName().equals("init") || method.getName().equals("destroy")) continue;
            Method onMethod = getOnMethod(serviceClass, method);
            SncpAction en = new SncpAction(method, onMethod == null ? Sncp.hash(method) : Sncp.hash(onMethod));
            if (actionids.contains(en.actionid)) {
                throw new RuntimeException(serviceClass.getName() + " have one more same action(Method=" + method + ", actionid=" + en.actionid + ")");
            }
            methodens.add(en);
            actionids.add(en.actionid);
        }
        this.actions = methodens.toArray(new SncpAction[methodens.size()]);
        logger.fine("Load " + this.getClass().getSimpleName() + "(serviceClass = " + serviceClass.getName() + ", serviceid =" + serviceid + ", serviceName =" + serviceName + ", actions = " + methodens + ")");
    }

    public static Method getOnMethod(final Class serviceClass, Method method) {
        Method onMethod = null;
        if (method.getAnnotation(RemoteOn.class) != null) {
            char[] ms = method.getName().toCharArray();
            ms[0] = Character.toUpperCase(ms[0]);
            try {
                onMethod = serviceClass.getMethod("on" + new String(ms), method.getParameterTypes());
                if (onMethod.getReturnType() != method.getReturnType()) {
                    throw new RuntimeException(serviceClass.getName() + " (Method=" + method + ") and (Method=" + onMethod + ") has not same returnType");
                }
                if (!Modifier.isFinal(onMethod.getModifiers())) {
                    throw new RuntimeException(serviceClass.getName() + " (Method=" + method + ") is not final");
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(serviceClass.getName() + " not found (Public Method=" + "on" + new String(ms) + "but " + method + " has @" + RemoteOn.class.getSimpleName());
            }
        }
        return onMethod;
    }

    public <T> T remote(final BsonConvert convert, Transport transport, final int index, final Object... params) {
        return convert.convertFrom(actions[index].resultTypes, send(convert, transport, index, params));
    }

    private void fillHeader(ByteBuffer buffer, long seqid, DLong actionid, int frameCount, int frameIndex, int bodyLength) {
        //---------------------head----------------------------------
        buffer.putLong(seqid); //序列号
        buffer.putChar((char) HEADER_SIZE); //header长度
        buffer.putLong(this.serviceid);
        buffer.putLong(this.nameid);
        buffer.putLong(actionid.getFirst());
        buffer.putLong(actionid.getSecond());
        buffer.put((byte) frameCount); //数据的帧数， 最小值为1
        buffer.put((byte) frameIndex); //数据的帧数序号， 从frame.count-1开始, 0表示最后一帧
        buffer.putInt(0); //结果码， 请求方固定传0
        buffer.putInt(bodyLength); //body长度
    }

    private byte[] send(final BsonConvert convert, Transport transport, final int index, Object... params) {
        int bodyLength = 2;
        Type[] myparamtypes = actions[index].paramTypes;
        byte[][] bytesarray = new byte[params.length][];
        for (int i = 0; i < bytesarray.length; i++) {
            bytesarray[i] = convert.convertTo(myparamtypes[i], params[i]);
            bodyLength += 4 + bytesarray[i].length;
        }
        final SncpAction action = actions[index];
        final long seqid = System.nanoTime();
        final DLong actionid = action.actionid;
        final ByteBuffer buffer = transport.pollBuffer();
        final AsyncConnection conn = transport.pollConnection();
        final int readto = conn.getReadTimeoutSecond();
        final int writeto = conn.getWriteTimeoutSecond();
        try {
            if ((HEADER_SIZE + bodyLength) > buffer.limit()) {
                if (debug) logger.finest(this.serviceid + "," + this.nameid + "," + action + " sncp length : " + (HEADER_SIZE + bodyLength));
                final int patch = bodyLength / (buffer.capacity() - HEADER_SIZE) + (bodyLength % (buffer.capacity() - HEADER_SIZE) > 0 ? 1 : 0);
                int pos = 0;
                final byte[] all = new byte[bodyLength];
                all[pos++] = (byte) ((bytesarray.length & 0xff00) >> 8);
                all[pos++] = (byte) (bytesarray.length & 0xff);
                for (byte[] bs : bytesarray) {
                    all[pos++] = (byte) ((bs.length & 0xff000000) >> 24);
                    all[pos++] = (byte) ((bs.length & 0xff0000) >> 16);
                    all[pos++] = (byte) ((bs.length & 0xff00) >> 8);
                    all[pos++] = (byte) (bs.length & 0xff);
                    System.arraycopy(bs, 0, all, pos, bs.length);
                    pos += bs.length;
                }
                if (pos != all.length) logger.warning(this.serviceid + "," + this.nameid + "," + action + " sncp body.length : " + all.length + ", but pos=" + pos);
                pos = 0;
                for (int i = patch - 1; i >= 0; i--) {
                    fillHeader(buffer, seqid, actionid, patch, i, bodyLength);
                    int len = Math.min(buffer.remaining(), all.length - pos);
                    buffer.put(all, pos, len);
                    pos += len;
                    buffer.flip();
                    Thread.sleep(10);
                    conn.write(buffer).get(writeto > 0 ? writeto : 5, TimeUnit.SECONDS);
                    buffer.clear();
                }
            } else {  //只有一帧的数据
                {
                    //---------------------head----------------------------------
                    fillHeader(buffer, seqid, actionid, 1, 0, bodyLength);
                    //---------------------body----------------------------------
                    buffer.putChar((char) bytesarray.length); //参数数组大小
                    for (byte[] bs : bytesarray) {
                        buffer.putInt(bs.length);
                        buffer.put(bs);
                    }
                    buffer.flip();
                }
                conn.write(buffer).get(writeto > 0 ? writeto : 5, TimeUnit.SECONDS);
                buffer.clear();
            }
            conn.read(buffer).get(readto > 0 ? readto : 5, TimeUnit.SECONDS);
            buffer.flip();
            long rseqid = buffer.getLong();
            if (rseqid != seqid) throw new RuntimeException("sncp send seqid = " + seqid + ", but receive seqid =" + rseqid);
            if (buffer.getChar() != HEADER_SIZE) throw new RuntimeException("sncp buffer receive header.length not " + HEADER_SIZE);
            long rserviceid = buffer.getLong();
            if (rserviceid != serviceid) throw new RuntimeException("sncp send serviceid = " + serviceid + ", but receive serviceid =" + rserviceid);
            long rnameid = buffer.getLong();
            if (rnameid != nameid) throw new RuntimeException("sncp send nameid = " + nameid + ", but receive nameid =" + rnameid);
            long ractionid1 = buffer.getLong();
            long ractionid2 = buffer.getLong();
            if (!actionid.compare(ractionid1, ractionid2)) throw new RuntimeException("sncp send actionid = " + actionid + ", but receive actionid =(" + ractionid1 + "_" + ractionid2 + ")");
            final int frameCount = buffer.get();
            if (frameCount < 1) throw new RuntimeException("sncp send nameid = " + nameid + ", but frame.count =" + frameCount);
            int frameIndex = buffer.get();
            if (frameIndex < 0 || frameIndex >= frameCount) throw new RuntimeException("sncp send nameid = " + nameid + ", but frame.count =" + frameCount + " & frame.index =" + frameIndex);
            final int retcode = buffer.getInt();
            if (retcode != 0) throw new RuntimeException("remote service deal error (receive retcode =" + retcode + ")");
            final int bodylen = buffer.getInt();
            final byte[] body = new byte[bodylen];
            if (frameCount == 1) {
                buffer.get(body);
                return body;
            } else {
                int received = 0;
                for (int i = 0; i < frameCount; i++) {
                    received += buffer.remaining();
                    buffer.get(body, (frameCount - frameIndex - 1) * (buffer.capacity() - HEADER_SIZE), buffer.remaining());
                    if (i == frameCount - 1) break;
                    buffer.clear();
                    conn.read(buffer).get(readto > 0 ? readto : 5, TimeUnit.SECONDS);
                    buffer.flip();
                    rseqid = buffer.getLong();
                    if (rseqid != seqid) throw new RuntimeException("sncp send seqid = " + seqid + ", but receive next.seqid =" + rseqid);
                    if (buffer.getChar() != HEADER_SIZE) throw new RuntimeException("sncp buffer receive header.length not " + HEADER_SIZE);
                    rserviceid = buffer.getLong();
                    if (rserviceid != serviceid) throw new RuntimeException("sncp send serviceid = " + serviceid + ", but receive next.serviceid =" + rserviceid);
                    rnameid = buffer.getLong();
                    if (rnameid != nameid) throw new RuntimeException("sncp send nameid = " + nameid + ", but receive next.nameid =" + rnameid);
                    ractionid1 = buffer.getLong();
                    ractionid2 = buffer.getLong();
                    if (!actionid.compare(ractionid1, ractionid2)) throw new RuntimeException("sncp send actionid = " + actionid + ", but receive next.actionid =(" + ractionid1 + "_" + ractionid2 + ")");
                    if (buffer.get() < 1) throw new RuntimeException("sncp send nameid = " + nameid + ", but next.frame.count != " + frameCount);
                    frameIndex = buffer.get();
                    if (frameIndex < 0 || frameIndex >= frameCount) throw new RuntimeException("sncp receive nameid = " + nameid + ", but frame.count =" + frameCount + " & next.frame.index =" + frameIndex);
                    int rretcode = buffer.getInt();
                    if (rretcode != 0) throw new RuntimeException("remote service deal error (receive retcode =" + rretcode + ")");
                    int rbodylen = buffer.getInt();
                    if (rbodylen != bodylen) throw new RuntimeException("sncp receive bodylength = " + bodylen + ", but receive next.bodylength =" + rbodylen);
                }
                if (received != bodylen) throw new RuntimeException("sncp receive bodylength = " + bodylen + ", but receive next.receivedlength =" + received);
                return body;
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            transport.offerBuffer(buffer);
            transport.offerConnection(conn);
        }
    }

}
