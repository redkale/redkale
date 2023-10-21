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

    /**
     * 是否开启了trace功能
     *
     * @return
     */
    public static boolean enable() {
        return enable;
    }

    /**
     * 创建一个新的traceid
     *
     * @return
     */
    public static String createTraceid() {
        return enable ? tidSupplier.get() : null;
    }

    /**
     * 获取当前线程的traceid
     *
     * @return
     */
    public static String currentTraceid() {
        return enable ? localTrace.get() : null;
    }

    /**
     * 移除当前线程的traceid
     */
    public static void removeTraceid() {
        if (enable) {
            localTrace.remove();
        }
    }

    /**
     * 设置当前线程的traceid， 如果参数为空则清除当前线程traceid
     *
     * @param traceid
     *
     * @return
     */
    public static void currentTraceid(String traceid) {
        if (enable) {
            if (traceid != null && !traceid.isEmpty()) {
                localTrace.set(traceid);
            } else {
                localTrace.remove();
            }
        }
    }

    /**
     * 设置当前线程的traceid， 若参数为空则会创建一个新的traceid
     *
     * @param traceid
     *
     * @return
     */
    public static String computeIfAbsent(String traceid) {
        if (enable) {
            String rs = traceid;
            if (rs == null || rs.isEmpty()) {
                rs = tidSupplier.get();
            }
            localTrace.set(rs);
            return rs;
        }
        return traceid;
    }

    /**
     * 设置当前线程的traceid， 若参数1为空，则使用参数2，若参数2未空，则会创建一个新的traceid
     *
     * @param traceid
     * @param traceid2
     *
     * @return
     */
    public static String computeIfAbsent(String traceid, String traceid2) {
        if (enable) {
            String rs = traceid;
            if (rs == null || rs.isEmpty()) {
                if (traceid2 == null || traceid2.isEmpty()) {
                    rs = tidSupplier.get();
                } else {
                    rs = traceid2;
                }
            }
            localTrace.set(rs);
            return rs;
        }
        return traceid;
    }
}
