/*
 */
package org.redkale.util;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 创建traceid工具类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class Traces {

    private static final boolean enable = !Boolean.getBoolean("redkale.trace.disable");

    private static final String PROCESS_ID = UUID.randomUUID().toString().replaceAll("-", "");

    private static final ThreadLocal<IdSequence> THREAD_SEQUENCE = ThreadLocal.withInitial(IdSequence::new);

    private static final ThreadLocal<String> localTrace = new ThreadLocal<>();

    //借用Skywalking的GlobalIdGenerator生成ID的规则
    private static final Supplier<String> tidSupplier = () -> PROCESS_ID
        + "." + Thread.currentThread().getId()
        + "." + THREAD_SEQUENCE.get().nextSeq();

    public static boolean enable() {
        return enable;
    }

    public static String createTraceid() {
        return enable ? tidSupplier.get() : null;
    }

    public static String currentTraceid() {
        return enable ? localTrace.get() : null;
    }

    public static String computeIfAbsent(String requestTraceid) {
        if (enable) {
            String rs = requestTraceid;
            if (rs == null || rs.isEmpty()) {
                rs = tidSupplier.get();
            }
            localTrace.set(rs);
            return rs;
        }
        return requestTraceid;
    }

    private static class IdSequence {

        private long lastTimestamp;

        private short threadSeq;

        private long lastShiftTimestamp;

        private int lastShiftValue;

        public IdSequence() {
            this.lastTimestamp = System.currentTimeMillis();
        }

        public long nextSeq() {
            long rs = timestamp() * 10000;
            if (threadSeq == 10000) {
                threadSeq = 0;
            }
            return rs + threadSeq++;
        }

        private long timestamp() {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis < lastTimestamp) {
                // Just for considering time-shift-back by Ops or OS. @hanahmily 's suggestion.
                if (lastShiftTimestamp != currentTimeMillis) {
                    lastShiftValue++;
                    lastShiftTimestamp = currentTimeMillis;
                }
                return lastShiftValue;
            } else {
                lastTimestamp = currentTimeMillis;
                return lastTimestamp;
            }
        }
    }
}
