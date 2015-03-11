/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.net.Async;
import com.wentch.redkale.net.Transport;
import com.wentch.redkale.convert.bson.BsonConvert;
import com.wentch.redkale.service.MultiService;
import com.wentch.redkale.service.RemoteOn;
import static com.wentch.redkale.net.sncp.SncpRequest.HEADER_SIZE;
import com.wentch.redkale.util.TwoLong;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;

/**
 *
 * @author zhangjx
 */
public final class SncpClient {

    private final Logger logger = Logger.getLogger(SncpClient.class.getSimpleName());

    protected static final class SncpAction {

        protected final TwoLong actionid;

        protected final Method method;

        protected final Type resultTypes;  //void 必须设为 null

        protected final Type[] paramTypes;

        protected final boolean async;

        public SncpAction(Method method, TwoLong actionid) {
            this.actionid = actionid;
            Type rt = method.getGenericReturnType();
            if (rt instanceof TypeVariable) {
                TypeVariable tv = (TypeVariable) rt;
                if (tv.getBounds().length == 1) rt = tv.getBounds()[0];
            }
            this.resultTypes = rt == void.class ? null : rt;
            this.paramTypes = method.getGenericParameterTypes();
            this.method = method;
            this.async = method.getReturnType() == void.class && method.getAnnotation(Async.class) != null;
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
        Set<TwoLong> actionids = new HashSet<>();
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

    private byte[] send(final BsonConvert convert, Transport transport, final int index, Object... params) {
        int bodyLength = 2;
        Type[] myparamtypes = actions[index].paramTypes;
        byte[][] bytesarray = new byte[params.length][];
        for (int i = 0; i < bytesarray.length; i++) {
            bytesarray[i] = convert.convertTo(myparamtypes[i], params[i]);
            bodyLength += 2 + bytesarray[i].length;
        }
        ByteBuffer buffer = transport.pollBuffer();
        if ((HEADER_SIZE + bodyLength) > buffer.limit()) {
            throw new RuntimeException("send buffer size too large(" + (HEADER_SIZE + bodyLength) + ")");
        }
        final SncpAction action = actions[index];
        final long seqid = System.nanoTime();
        final TwoLong actionid = action.actionid;
        {
            //---------------------head----------------------------------
            buffer.putLong(seqid); //序列号
            buffer.putChar((char) HEADER_SIZE); //header长度
            buffer.putLong(this.serviceid);
            buffer.putLong(this.nameid);
            buffer.putLong(actionid.getFirst());
            buffer.putLong(actionid.getSecond());
            buffer.put((byte) 0); //剩下还有多少帧数据， 0表示只有当前一帧数据
            buffer.putInt(0); //结果码， 请求方固定传0
            buffer.putChar((char) bodyLength); //body长度
            //---------------------body----------------------------------
            buffer.putChar((char) bytesarray.length); //参数数组大小
            for (byte[] bs : bytesarray) {
                buffer.putChar((char) bs.length);
                buffer.put(bs);
            }
            buffer.flip();
        }
        if (action.async) {
            transport.async(buffer, null, null);
            return null;
        }
        buffer = transport.send(buffer);

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
        int frame = buffer.get();
        int retcode = buffer.getInt();
        if (retcode != 0) throw new RuntimeException("remote service deal error (receive retcode =" + retcode + ")");
        int bodylen = buffer.getChar();
        byte[] bytes = new byte[bodylen];
        buffer.get(bytes);
        transport.offerBuffer(buffer);
        return bytes;
    }

}
