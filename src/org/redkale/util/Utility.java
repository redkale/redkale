/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.*;

/**
 *
 * 常见操作的工具类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Utility {

    private static final int zoneRawOffset = TimeZone.getDefault().getRawOffset();

    private static final String format = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final char hex[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static final sun.misc.Unsafe UNSAFE;

    private static final long strvaloffset;

    private static final long sbvaloffset;

    private static final javax.net.ssl.SSLContext DEFAULTSSL_CONTEXT;

    private static final javax.net.ssl.HostnameVerifier defaultVerifier = (s, ss) -> true;

    static {
        sun.misc.Unsafe usafe = null;
        long fd1 = 0L;
        long fd2 = 0L;
        try {
            Field f = String.class.getDeclaredField("value");
            if (f.getType() == char[].class) { //JDK9及以上不再是char[]
                Field safeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                safeField.setAccessible(true);
                usafe = (sun.misc.Unsafe) safeField.get(null);
                fd1 = usafe.objectFieldOffset(f);
                fd2 = usafe.objectFieldOffset(StringBuilder.class.getSuperclass().getDeclaredField("value"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e); //不可能会发生
        }
        UNSAFE = usafe;
        strvaloffset = fd1;
        sbvaloffset = fd2;

        try {
            DEFAULTSSL_CONTEXT = javax.net.ssl.SSLContext.getInstance("SSL");
            DEFAULTSSL_CONTEXT.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws java.security.cert.CertificateException {
                }

                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) throws java.security.cert.CertificateException {
                }
            }}, null);
        } catch (Exception e) {
            throw new RuntimeException(e); //不可能会发生
        }
    }

    private Utility() {
    }

    /**
     * 将多个key:value的字符串键值对组合成一个Map，items长度必须是偶数, 参数个数若是奇数的话，最后一个会被忽略
     * 类似 JDK9中的 Map.of 方法
     *
     * @param items 键值对
     *
     * @return Map
     */
    public static Map<String, String> ofMap(String... items) {
        HashMap<String, String> map = new HashMap<>();
        int len = items.length / 2;
        for (int i = 0; i < len; i++) {
            map.put(items[i * 2], items[i * 2 + 1]);
        }
        return map;
    }

    /**
     * 将多个key:value对应值组合成一个Map，items长度必须是偶数, 参数个数若是奇数的话，最后一个会被忽略
     * 类似 JDK9中的 Map.of 方法
     *
     * @param items 键值对
     *
     * @return Map
     */
    public static Map<Object, Object> ofMap(Object... items) {
        HashMap<Object, Object> map = new HashMap<>();
        int len = items.length / 2;
        for (int i = 0; i < len; i++) {
            map.put(items[i * 2], items[i * 2 + 1]);
        }
        return map;
    }

    /**
     * 将多个元素组合成一个Set
     *
     * @param <T>   泛型
     * @param items 元素
     *
     * @return Set
     */
    public static <T> Set<T> ofSet(T... items) {
        Set<T> set = new LinkedHashSet<>();
        for (T item : items) set.add(item);
        return set;
    }

    /**
     * 将多个元素组合成一个List
     *
     * @param <T>   泛型
     * @param items 元素
     *
     * @return List
     */
    public static <T> List<T> ofList(T... items) {
        List<T> list = new ArrayList<>();
        for (T item : items) list.add(item);
        return list;
    }

    /**
     * 获取不带"-"的UUID值
     *
     * @return 不带"-"UUID值
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 数组上追加数据
     *
     * @param <T>   泛型
     * @param array 原数组
     * @param objs  待追加数据
     *
     * @return 新数组
     */
    public static <T> T[] append(final T[] array, final T... objs) {
        if (array == null || array.length == 0) return objs;
        final T[] news = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length + objs.length);
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 数组上追加数据
     *
     * @param <T>   泛型
     * @param array 原数组
     * @param objs  待追加数据
     *
     * @return 新数组
     */
    public static <T> T[] append(final T[] array, final Collection<T> objs) {
        if (objs == null || objs.isEmpty()) return array;
        if (array == null) {
            T one = null;
            for (T t : objs) {
                if (t != null) one = t;
                break;
            }
            if (one == null) return array;
            T[] news = (T[]) Array.newInstance(one.getClass(), objs.size());
            return objs.toArray(news);
        }
        T[] news = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length + objs.size());
        System.arraycopy(array, 0, news, 0, array.length);
        int index = -1;
        for (T t : objs) {
            news[array.length + (++index)] = t;
        }
        return news;
    }

    /**
     * 判断字符串是否包含指定的字符，包含返回true
     *
     * @param string 字符串
     * @param values 字符集合
     *
     * @return boolean
     */
    public static boolean contains(String string, char... values) {
        if (string == null) return false;
        for (char ch : Utility.charArray(string)) {
            for (char ch2 : values) {
                if (ch == ch2) return true;
            }
        }
        return false;
    }

    /**
     * 删除掉字符串数组中包含指定的字符串
     *
     * @param columns 待删除数组
     * @param cols    需排除的字符串
     *
     * @return 新字符串数组
     */
    public static String[] exclude(final String[] columns, final String... cols) {
        if (columns == null || columns.length == 0 || cols == null || cols.length == 0) return columns;
        int count = 0;
        for (String column : columns) {
            boolean flag = false;
            for (String col : cols) {
                if (column != null && column.equals(col)) {
                    flag = true;
                    break;
                }
            }
            if (flag) count++;
        }
        if (count == 0) return columns;
        if (count == columns.length) return new String[0];
        final String[] newcols = new String[columns.length - count];
        count = 0;
        for (String column : columns) {
            boolean flag = false;
            for (String col : cols) {
                if (column != null && column.equals(col)) {
                    flag = true;
                    break;
                }
            }
            if (!flag) newcols[count++] = column;
        }
        return newcols;
    }

    /**
     * 将buffer的内容转换成字符串, string参数不为空时会追加在buffer内容字符串之前
     *
     * @param string 字符串前缀
     * @param buffer ByteBuffer
     *
     * @return 字符串
     */
    public static String toString(String string, ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) return string;
        int pos = buffer.position();
        int limit = buffer.limit();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.position(pos);
        buffer.limit(limit);
        if (string == null) return new String(bytes, UTF_8);
        return string + new String(bytes, UTF_8);
    }

    /**
     * 将buffer的内容转换成字符串并打印到控制台, string参数不为空时会追加在buffer内容字符串之前
     *
     * @param string 字符串前缀
     * @param buffer ByteBuffer
     *
     */
    public static void println(String string, ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) return;
        int pos = buffer.position();
        int limit = buffer.limit();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.position(pos);
        buffer.limit(limit);
        println(string, bytes);
    }

    /**
     * 将字节数组的内容转换成字符串并打印到控制台, string参数不为空时会追加在字节数组内容字符串之前
     *
     * @param string 字符串前缀
     * @param bytes  字节数组
     *
     */
    public static void println(String string, byte... bytes) {
        if (bytes == null) return;
        StringBuilder sb = new StringBuilder();
        if (string != null) sb.append(string);
        sb.append(bytes.length).append(".[");
        boolean last = false;
        for (byte b : bytes) {
            if (last) sb.append(',');
            int v = b & 0xff;
            sb.append("0x");
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v));
            last = true;
        }
        sb.append(']');
        (System.out).println(sb);
    }

    /**
     * 返回本机的第一个内网IPv4地址， 没有则返回null
     *
     * @return IPv4地址
     */
    public static InetAddress localInetAddress() {
        InetAddress back = null;
        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
            while (nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                if (!nif.isUp()) continue;
                Enumeration<InetAddress> eis = nif.getInetAddresses();
                while (eis.hasMoreElements()) {
                    InetAddress ia = eis.nextElement();
                    if (ia.isLoopbackAddress()) back = ia;
                    if (ia.isSiteLocalAddress()) return ia;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return back;
    }

    /**
     * 获取格式为yyyy-MM-dd HH:mm:ss的当前时间
     *
     * @return 格式为yyyy-MM-dd HH:mm:ss的时间值
     */
    public static String now() {
        return String.format(format, System.currentTimeMillis());
    }

    /**
     * 将指定时间格式化为 yyyy-MM-dd HH:mm:ss
     *
     * @param time 待格式化的时间
     *
     * @return 格式为yyyy-MM-dd HH:mm:ss的时间值
     */
    public static String formatTime(long time) {
        return String.format(format, time);
    }

    /**
     * 将时间值转换为长度为9的36进制值，8位的前面补充0
     *
     * @param time 时间值
     *
     * @return 36进制时间值
     */
    public static String format36time(long time) {
        String time36 = Long.toString(time, 36);
        return time36.length() < 9 ? ("0" + time36) : time36;
    }

    /**
     * 获取当天凌晨零点的格林时间
     *
     * @return 毫秒数
     */
    public static long midnight() {
        return midnight(System.currentTimeMillis());
    }

    /**
     * 获取指定时间当天凌晨零点的格林时间
     *
     * @param time 指定时间
     *
     * @return 毫秒数
     */
    public static long midnight(long time) {
        return (time + zoneRawOffset) / 86400000 * 86400000 - zoneRawOffset;
    }

    /**
     * 获取当天20151231格式的int值
     *
     * @return 20151231格式的int值
     */
    public static int today() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return today.getYear() * 10000 + today.getMonthValue() * 100 + today.getDayOfMonth();
    }

    /**
     * 获取昨天20151230格式的int值
     *
     * @return 20151230格式的int值
     */
    public static int yesterday() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取指定时间的20160202格式的int值
     *
     * @param time 指定时间
     *
     * @return 毫秒数
     */
    public static int yyyyMMdd(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取时间点所在星期的周一
     *
     * @param time 指定时间
     *
     * @return 毫秒数
     */
    public static long monday(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate();
        ld = ld.minusDays(ld.getDayOfWeek().getValue() - 1);
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    /**
     * 获取时间点所在星期的周日
     *
     * @param time 指定时间
     *
     * @return 毫秒数
     */
    public static long sunday(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate();
        ld = ld.plusDays(7 - ld.getDayOfWeek().getValue());
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    /**
     * 获取时间点所在月份的1号
     *
     * @param time 指定时间
     *
     * @return 毫秒数
     */
    public static long monthFirstDay(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate().withDayOfMonth(1);
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    /**
     * 获取时间点所在月份的最后一天
     *
     * @param time 指定时间
     *
     * @return 毫秒数
     */
    public static long monthLastDay(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate();
        ld = ld.withDayOfMonth(ld.lengthOfMonth());
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    /**
     * 将字节数组转换为16进制字符串
     *
     * @param bytes 字节数组
     *
     * @return 16进制字符串
     */
    public static String binToHexString(byte[] bytes) {
        return new String(binToHex(bytes));
    }

    /**
     * 将字节数组转换为16进制字符数组
     *
     * @param bytes 字节数组
     *
     * @return 16进制字符串的字符数组
     */
    public static char[] binToHex(byte[] bytes) {
        return binToHex(bytes, 0, bytes.length);
    }

    /**
     * 将字节数组转换为16进制字符串
     *
     * @param bytes  字节数组
     * @param offset 偏移量
     * @param len    长度
     *
     * @return 16进制字符串
     */
    public static String binToHexString(byte[] bytes, int offset, int len) {
        return new String(binToHex(bytes, offset, len));
    }

    /**
     * 将字节数组转换为16进制字符数组
     *
     * @param bytes  字节数组
     * @param offset 偏移量
     * @param len    长度
     *
     * @return 16进制字符串的字符数组
     */
    public static char[] binToHex(byte[] bytes, int offset, int len) {
        final char[] sb = new char[len * 2];
        final int end = offset + len;
        int index = 0;
        final char[] hexs = hex;
        for (int i = offset; i < end; i++) {
            byte b = bytes[i];
            sb[index++] = (hexs[((b >> 4) & 0xF)]);
            sb[index++] = hexs[((b) & 0xF)];
        }
        return sb;
    }

    /**
     * 将16进制字符串转换成字节数组
     *
     * @param src 16进制字符串
     *
     * @return 字节数组
     */
    public static byte[] hexToBin(CharSequence src) {
        return hexToBin(src, 0, src.length());
    }

    /**
     *
     * 将16进制字符串转换成字节数组
     *
     * @param src    16进制字符串
     * @param offset 偏移量
     * @param len    长度
     *
     * @return 字节数组
     */
    public static byte[] hexToBin(CharSequence src, int offset, int len) {
        final int size = (len + 1) / 2;
        final byte[] bytes = new byte[size];
        String digits = "0123456789abcdef";
        for (int i = 0; i < size; i++) {
            int ch1 = src.charAt(offset + i * 2);
            if ('A' <= ch1 && 'F' >= ch1) ch1 = ch1 - 'A' + 'a';
            int ch2 = src.charAt(offset + i * 2 + 1);
            if ('A' <= ch2 && 'F' >= ch2) ch2 = ch2 - 'A' + 'a';
            int pos1 = digits.indexOf(ch1);
            if (pos1 < 0) throw new NumberFormatException();
            int pos2 = digits.indexOf(ch2);
            if (pos2 < 0) throw new NumberFormatException();
            bytes[i] = (byte) (pos1 * 0x10 + pos2);
        }
        return bytes;
    }

    /**
     *
     * 将16进制字符串转换成字节数组
     *
     * @param str 16进制字符串
     *
     * @return 字节数组
     */
    public static byte[] hexToBin(String str) {
        return hexToBin(charArray(str));
    }

    /**
     *
     * 将16进制字符数组转换成字节数组
     *
     * @param src 16进制字符数组
     *
     * @return 字节数组
     */
    public static byte[] hexToBin(char[] src) {
        return hexToBin(src, 0, src.length);
    }

    /**
     * 将16进制字符数组转换成字节数组
     *
     * @param src    16进制字符数组
     * @param offset 偏移量
     * @param len    长度
     *
     * @return 字节数组
     */
    public static byte[] hexToBin(char[] src, int offset, int len) {
        final int size = (len + 1) / 2;
        final byte[] bytes = new byte[size];
        String digits = "0123456789abcdef";
        for (int i = 0; i < size; i++) {
            int ch1 = src[offset + i * 2];
            if ('A' <= ch1 && 'F' >= ch1) ch1 = ch1 - 'A' + 'a';
            int ch2 = src[offset + i * 2 + 1];
            if ('A' <= ch2 && 'F' >= ch2) ch2 = ch2 - 'A' + 'a';
            int pos1 = digits.indexOf(ch1);
            if (pos1 < 0) throw new NumberFormatException();
            int pos2 = digits.indexOf(ch2);
            if (pos2 < 0) throw new NumberFormatException();
            bytes[i] = (byte) (pos1 * 0x10 + pos2);
        }
        return bytes;
    }

    //-----------------------------------------------------------------------------
    /**
     * 使用UTF-8编码将byte[]转换成char[]
     *
     * @param array byte[]
     *
     * @return char[]
     */
    public static char[] decodeUTF8(final byte[] array) {
        return decodeUTF8(array, 0, array.length);
    }

    public static char[] decodeUTF8(final byte[] array, final int start, final int len) {
        byte b;
        int size = len;
        final byte[] bytes = array;
        final int limit = start + len;
        for (int i = start; i < limit; i++) {
            b = bytes[i];
            if ((b >> 5) == -2) {
                size--;
            } else if ((b >> 4) == -2) {
                size -= 2;
            }
        }
        final char[] text = new char[size];
        size = 0;
        for (int i = start; i < limit;) {
            b = bytes[i++];
            if (b >= 0) {
                text[size++] = (char) b;
            } else if ((b >> 5) == -2) {
                text[size++] = (char) (((b << 6) ^ bytes[i++]) ^ (((byte) 0xC0 << 6) ^ ((byte) 0x80)));
            } else if ((b >> 4) == -2) {
                text[size++] = (char) ((b << 12) ^ (bytes[i++] << 6) ^ (bytes[i++] ^ (((byte) 0xE0 << 12) ^ ((byte) 0x80 << 6) ^ ((byte) 0x80))));
            }
        }
        return text;
    }

    public static byte[] encodeUTF8(final String value) {
        if (value == null) return new byte[0];
        if (UNSAFE == null) return encodeUTF8(value.toCharArray());
        return encodeUTF8((char[]) UNSAFE.getObject(value, strvaloffset));
    }

    public static byte[] encodeUTF8(final char[] array) {
        return encodeUTF8(array, 0, array.length);
    }

    public static byte[] encodeUTF8(final char[] text, final int start, final int len) {
        char c;
        int size = 0;
        final char[] chars = text;
        final int limit = start + len;
        for (int i = start; i < limit; i++) {
            c = chars[i];
            if (c < 0x80) {
                size++;
            } else if (c < 0x800) {
                size += 2;
            } else {
                size += 3;
            }
        }
        final byte[] bytes = new byte[size];
        size = 0;
        for (int i = start; i < limit; i++) {
            c = chars[i];
            if (c < 0x80) {
                bytes[size++] = (byte) c;
            } else if (c < 0x800) {
                bytes[size++] = (byte) (0xc0 | (c >> 6));
                bytes[size++] = (byte) (0x80 | (c & 0x3f));
            } else {
                bytes[size++] = (byte) (0xe0 | ((c >> 12)));
                bytes[size++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                bytes[size++] = (byte) (0x80 | (c & 0x3f));
            }
        }
        return bytes;
    }

    public static char[] charArray(String value) {
        if (value == null) return null;
        if (UNSAFE == null) return value.toCharArray();
        return (char[]) UNSAFE.getObject(value, strvaloffset);
    }

    public static char[] charArray(StringBuilder value) {
        if (value == null) return null;
        if (UNSAFE == null) return value.toString().toCharArray();
        return (char[]) UNSAFE.getObject(value, sbvaloffset);
    }

    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, final char[] array) {
        return encodeUTF8(buffer, array, 0, array.length);
    }

    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, int bytesLength, final char[] array) {
        return encodeUTF8(buffer, bytesLength, array, 0, array.length);
    }

    public static int encodeUTF8Length(String value) {
        if (value == null) return -1;
        if (UNSAFE == null) return encodeUTF8Length(value.toCharArray());
        return encodeUTF8Length((char[]) UNSAFE.getObject(value, strvaloffset));
    }

    public static int encodeUTF8Length(final char[] text) {
        return encodeUTF8Length(text, 0, text.length);
    }

    public static int encodeUTF8Length(final char[] text, final int start, final int len) {
        char c;
        int size = 0;
        final char[] chars = text;
        final int limit = start + len;
        for (int i = start; i < limit; i++) {
            c = chars[i];
            size += (c < 0x80 ? 1 : (c < 0x800 ? 2 : 3));
        }
        return size;
    }

    /**
     * 将两个数字组装成一个long
     *
     * @param high 高位值
     * @param low  低位值
     *
     * @return long值
     */
    public static long merge(int high, int low) {
        return (0L + high) << 32 | low;
    }

    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, final char[] text, final int start, final int len) {
        return encodeUTF8(buffer, encodeUTF8Length(text, start, len), text, start, len);
    }

    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, int bytesLength, final char[] text, final int start, final int len) {
        char c;
        char[] chars = text;
        final int limit = start + len;
        int remain = buffer.remaining();
        final ByteBuffer buffer2 = remain >= bytesLength ? null : ByteBuffer.allocate(bytesLength - remain + 3); //最差情况buffer最后两byte没有填充
        ByteBuffer buf = buffer;
        for (int i = start; i < limit; i++) {
            c = chars[i];
            if (c < 0x80) {
                if (buf.remaining() < 1) buf = buffer2;
                buf.put((byte) c);
            } else if (c < 0x800) {
                if (buf.remaining() < 2) buf = buffer2;
                buf.put((byte) (0xc0 | (c >> 6)));
                buf.put((byte) (0x80 | (c & 0x3f)));
            } else {
                if (buf.remaining() < 3) buf = buffer2;
                buf.put((byte) (0xe0 | ((c >> 12))));
                buf.put((byte) (0x80 | ((c >> 6) & 0x3f)));
                buf.put((byte) (0x80 | (c & 0x3f)));
            }
        }
        if (buffer2 != null) buffer2.flip();
        return buffer2;
    }

    //-----------------------------------------------------------------------------
    public static javax.net.ssl.SSLContext getDefaultSSLContext() {
        return DEFAULTSSL_CONTEXT;
    }

    public static javax.net.ssl.HostnameVerifier getDefaultHostnameVerifier() {
        return defaultVerifier;
    }

    public static Socket createDefaultSSLSocket(InetSocketAddress address) throws IOException {
        return createDefaultSSLSocket(address.getAddress(), address.getPort());
    }

    public static Socket createDefaultSSLSocket(InetAddress host, int port) throws IOException {
        Socket socket = DEFAULTSSL_CONTEXT.getSocketFactory().createSocket(host, port);
        return socket;
    }

    public static String postHttpContent(String url) throws IOException {
        return remoteHttpContent(null, "POST", url, 0, null, null).toString("UTF-8");
    }

    public static String postHttpContent(String url, int timeout) throws IOException {
        return remoteHttpContent(null, "POST", url, timeout, null, null).toString("UTF-8");
    }

    public static String postHttpContent(String url, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, 0, null, body).toString("UTF-8");
    }

    public static String postHttpContent(String url, int timeout, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, timeout, null, body).toString("UTF-8");
    }

    public static String postHttpContent(String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, 0, headers, body).toString("UTF-8");
    }

    public static String postHttpContent(String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, timeout, headers, body).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "POST", url, 0, null, null).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url, int timeout) throws IOException {
        return remoteHttpContent(ctx, "POST", url, timeout, null, null).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, 0, null, body).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url, int timeout, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, timeout, null, body).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, 0, headers, body).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, timeout, headers, body).toString("UTF-8");
    }

    public static String postHttpContent(String url, Charset charset) throws IOException {
        return remoteHttpContent(null, "POST", url, 0, null, null).toString(charset.name());
    }

    public static String postHttpContent(String url, int timeout, Charset charset) throws IOException {
        return remoteHttpContent(null, "POST", url, timeout, null, null).toString(charset.name());
    }

    public static String postHttpContent(String url, Charset charset, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, 0, null, body).toString(charset.name());
    }

    public static String postHttpContent(String url, int timeout, Charset charset, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, timeout, null, body).toString(charset.name());
    }

    public static String postHttpContent(String url, Charset charset, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, 0, headers, body).toString(charset.name());
    }

    public static String postHttpContent(String url, int timeout, Charset charset, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, timeout, headers, body).toString(charset.name());
    }

    public static String postHttpContent(SSLContext ctx, String url, Charset charset) throws IOException {
        return remoteHttpContent(ctx, "POST", url, 0, null, null).toString(charset.name());
    }

    public static String postHttpContent(SSLContext ctx, String url, int timeout, Charset charset) throws IOException {
        return remoteHttpContent(ctx, "POST", url, timeout, null, null).toString(charset.name());
    }

    public static String postHttpContent(SSLContext ctx, String url, Charset charset, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, 0, null, body).toString(charset.name());
    }

    public static String postHttpContent(SSLContext ctx, String url, int timeout, Charset charset, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, timeout, null, body).toString(charset.name());
    }

    public static String postHttpContent(SSLContext ctx, String url, Charset charset, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, 0, headers, body).toString(charset.name());
    }

    public static String postHttpContent(SSLContext ctx, String url, int timeout, Charset charset, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, timeout, headers, body).toString(charset.name());
    }

    public static byte[] postHttpBytesContent(String url) throws IOException {
        return remoteHttpContent(null, "POST", url, 0, null, null).toByteArray();
    }

    public static byte[] postHttpBytesContent(String url, int timeout) throws IOException {
        return remoteHttpContent(null, "POST", url, timeout, null, null).toByteArray();
    }

    public static byte[] postHttpBytesContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "POST", url, 0, null, null).toByteArray();
    }

    public static byte[] postHttpBytesContent(SSLContext ctx, String url, int timeout) throws IOException {
        return remoteHttpContent(ctx, "POST", url, timeout, null, null).toByteArray();
    }

    public static byte[] postHttpBytesContent(String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, 0, headers, body).toByteArray();
    }

    public static byte[] postHttpBytesContent(String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, timeout, headers, body).toByteArray();
    }

    public static byte[] postHttpBytesContent(SSLContext ctx, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, 0, headers, body).toByteArray();
    }

    public static byte[] postHttpBytesContent(SSLContext ctx, String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, timeout, headers, body).toByteArray();
    }

    public static String getHttpContent(String url) throws IOException {
        return remoteHttpContent(null, "GET", url, 0, null, null).toString("UTF-8");
    }

    public static String getHttpContent(String url, int timeout) throws IOException {
        return remoteHttpContent(null, "GET", url, timeout, null, null).toString("UTF-8");
    }

    public static String getHttpContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "GET", url, 0, null, null).toString("UTF-8");
    }

    public static String getHttpContent(SSLContext ctx, String url, int timeout) throws IOException {
        return remoteHttpContent(ctx, "GET", url, timeout, null, null).toString("UTF-8");
    }

    public static String getHttpContent(SSLContext ctx, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "GET", url, 0, headers, body).toString("UTF-8");
    }

    public static String getHttpContent(SSLContext ctx, String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "GET", url, timeout, headers, body).toString("UTF-8");
    }

    public static String getHttpContent(String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "GET", url, 0, headers, body).toString("UTF-8");
    }

    public static String getHttpContent(String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "GET", url, timeout, headers, body).toString("UTF-8");
    }

    public static String getHttpContent(String url, Charset charset) throws IOException {
        return remoteHttpContent(null, "GET", url, 0, null, null).toString(charset.name());
    }

    public static String getHttpContent(String url, int timeout, Charset charset) throws IOException {
        return remoteHttpContent(null, "GET", url, timeout, null, null).toString(charset.name());
    }

    public static String getHttpContent(SSLContext ctx, String url, Charset charset) throws IOException {
        return remoteHttpContent(ctx, "GET", url, 0, null, null).toString(charset.name());
    }

    public static String getHttpContent(SSLContext ctx, String url, int timeout, Charset charset) throws IOException {
        return remoteHttpContent(ctx, "GET", url, timeout, null, null).toString(charset.name());
    }

    public static String getHttpContent(SSLContext ctx, String url, Charset charset, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "GET", url, 0, headers, body).toString(charset.name());
    }

    public static String getHttpContent(SSLContext ctx, String url, int timeout, Charset charset, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "GET", url, timeout, headers, body).toString(charset.name());
    }

    public static String getHttpContent(String url, Charset charset, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "GET", url, 0, headers, body).toString(charset.name());
    }

    public static String getHttpContent(String url, int timeout, Charset charset, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "GET", url, timeout, headers, body).toString(charset.name());
    }

    public static byte[] getHttpBytesContent(String url) throws IOException {
        return remoteHttpContent(null, "GET", url, 0, null, null).toByteArray();
    }

    public static byte[] getHttpBytesContent(String url, int timeout) throws IOException {
        return remoteHttpContent(null, "GET", url, timeout, null, null).toByteArray();
    }

    public static byte[] getHttpBytesContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "GET", url, 0, null, null).toByteArray();
    }

    public static byte[] getHttpBytesContent(SSLContext ctx, String url, int timeout) throws IOException {
        return remoteHttpContent(ctx, "GET", url, timeout, null, null).toByteArray();
    }

    public static byte[] getHttpBytesContent(String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "GET", url, 0, headers, body).toByteArray();
    }

    public static byte[] getHttpBytesContent(String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "GET", url, timeout, headers, body).toByteArray();
    }

    public static byte[] getHttpBytesContent(SSLContext ctx, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "GET", url, 0, headers, body).toByteArray();
    }

    public static byte[] getHttpBytesContent(SSLContext ctx, String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "GET", url, timeout, headers, body).toByteArray();
    }

    public static ByteArrayOutputStream remoteHttpContent(String method, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, method, url, 0, headers, body);
    }

    public static ByteArrayOutputStream remoteHttpContent(String method, String url, int timeout, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, method, url, timeout, headers, body);
    }

    public static ByteArrayOutputStream remoteHttpContent(SSLContext ctx, String method, String url, int timeout, Map<String, String> headers, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeout > 0 ? timeout : 3000);
        conn.setReadTimeout(timeout > 0 ? timeout : 3000);
        if (conn instanceof HttpsURLConnection) {
            HttpsURLConnection httpsconn = ((HttpsURLConnection) conn);
            httpsconn.setSSLSocketFactory((ctx == null ? DEFAULTSSL_CONTEXT : ctx).getSocketFactory());
            httpsconn.setHostnameVerifier(defaultVerifier);
        }
        conn.setRequestMethod(method);
        if (headers != null) {
            for (Map.Entry<String, String> en : headers.entrySet()) { //不用forEach是为了兼容JDK 6
                conn.setRequestProperty(en.getKey(), en.getValue());
            }
        }
        if (body != null) {
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes(UTF_8));
        }
        conn.connect();
        int rs = conn.getResponseCode();
        if (rs == 301 || rs == 302) {
            String newurl = conn.getHeaderField("Location");
            conn.disconnect();
            return remoteHttpContent(ctx, method, newurl, timeout, headers, body);
        }
        InputStream in = (rs < 400 || rs == 404) && rs != 405 ? conn.getInputStream() : conn.getErrorStream();
        if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) in = new GZIPInputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] bytes = new byte[1024];
        int pos;
        while ((pos = in.read(bytes)) != -1) {
            out.write(bytes, 0, pos);
        }
        conn.disconnect();
        return out;
    }

    public static String read(InputStream in) throws IOException {
        return read(in, "UTF-8");
    }

    public static String read(InputStream in, String charsetName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] bytes = new byte[1024];
        int pos;
        while ((pos = in.read(bytes)) != -1) {
            out.write(bytes, 0, pos);
        }
        return charsetName == null ? out.toString() : out.toString(charsetName);
    }

    public static ByteArrayOutputStream readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] bytes = new byte[1024];
        int pos;
        while ((pos = in.read(bytes)) != -1) {
            out.write(bytes, 0, pos);
        }
        return out;
    }

    public static byte[] readBytes(InputStream in) throws IOException {
        return readStream(in).toByteArray();
    }

    public static ByteArrayOutputStream readStreamThenClose(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] bytes = new byte[1024];
        int pos;
        while ((pos = in.read(bytes)) != -1) {
            out.write(bytes, 0, pos);
        }
        in.close();
        return out;
    }

    public static byte[] readBytesThenClose(InputStream in) throws IOException {
        return readStreamThenClose(in).toByteArray();
    }
}
