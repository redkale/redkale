/*
 */
package org.redkale.util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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

    private static final AtomicLong sequence = new AtomicLong(System.currentTimeMillis());

    private static final Supplier<String> tidSupplier = () -> PROCESS_ID + Long.toHexString(sequence.incrementAndGet());

    private static final ThreadLocal<String> localTrace = new ThreadLocal<>();

    public static boolean enable() {
        return enable;
    }

    public static String createTraceid() {
        return enable ? tidSupplier.get() : null;
    }

    public static String currentTraceid() {
        return enable ? localTrace.get() : null;
    }

    public static void removeTraceid() {
        if (enable) {
            localTrace.remove();
        }
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
}
