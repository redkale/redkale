/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.util;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import javax.net.ssl.*;

/**
 *
 * @author zhangjx
 */
public final class Utility {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final char hex[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static final sun.misc.Unsafe UNSAFE;

    private static final long strvaloffset;

    static {
        sun.misc.Unsafe usafe = null;
        long fd = 0L;
        try {
            Field safeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            safeField.setAccessible(true);
            usafe = (sun.misc.Unsafe) safeField.get(null);
            fd = usafe.objectFieldOffset(String.class.getDeclaredField("value"));
        } catch (Exception e) {
            throw new RuntimeException(e); //不可能会发生
        }
        UNSAFE = usafe;
        strvaloffset = fd;
    }

    private Utility() {
    }

    public static void println(ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) return;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.put(bytes);
        buffer.flip();
        (System.out).println(Arrays.toString(bytes));
    }

    /**
     * 返回本机的第一个内网IPv4地址， 没有则返回null
     * <p>
     * @return
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

    public static int today() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return today.getYear() * 10000 + today.getMonthValue() * 100 + today.getDayOfMonth();
    }

    //时间点所在星期的周一
    public static long monday(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate();
        ld = ld.minusDays(ld.getDayOfWeek().getValue() - 1);
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    //时间点所在星期的周日
    public static long sunday(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate();
        ld = ld.plusDays(7 - ld.getDayOfWeek().getValue());
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    //时间点所在月份的1号
    public static long monthFirstDay(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate().withDayOfMonth(1);
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    public static String binToHexString(byte[] bytes) {
        return new String(binToHex(bytes));
    }

    public static char[] binToHex(byte[] bytes) {
        return binToHex(bytes, 0, bytes.length);
    }

    public static String binToHexString(byte[] bytes, int offset, int len) {
        return new String(binToHex(bytes, offset, len));
    }

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

    public static byte[] hexToBin(CharSequence src) {
        return hexToBin(src, 0, src.length());
    }

    public static byte[] hexToBin(CharSequence src, int offset, int len) {
        final int size = (len + 1) / 2;
        final byte[] bytes = new byte[size];
        final int end = offset + len;
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

    public static byte[] hexToBin(char[] src) {
        return hexToBin(src, 0, src.length);
    }

    public static byte[] hexToBin(char[] src, int offset, int len) {
        final int size = (len + 1) / 2;
        final byte[] bytes = new byte[size];
        final int end = offset + len;
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
        return value == null ? null : (char[]) UNSAFE.getObject(value, strvaloffset);
    }

    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, final char[] array) {
        return encodeUTF8(buffer, array, 0, array.length);
    }

    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, int bytesLength, final char[] array) {
        return encodeUTF8(buffer, bytesLength, array, 0, array.length);
    }

    public static int encodeUTF8Length(String value) {
        if (value == null) return -1;
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
    public static String postHttpContent(String url) throws IOException {
        return remoteHttpContent(null, "POST", url, null).toString("UTF-8");
    }

    public static String postHttpContent(String url, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, body).toString("UTF-8");
    }

    public static String getHttpContent(String url) throws IOException {
        return remoteHttpContent(null, "GET", url, null).toString("UTF-8");
    }

    public static byte[] getHttpBytesContent(String url) throws IOException {
        return remoteHttpContent(null, "GET", url, null).toByteArray();
    }

    public static String postHttpContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "POST", url, null).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, body).toString("UTF-8");
    }

    public static String getHttpContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "GET", url, null).toString("UTF-8");
    }

    public static byte[] getHttpBytesContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "GET", url, null).toByteArray();
    }

    protected static ByteArrayOutputStream remoteHttpContent(SSLContext ctx, String method, String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        if (ctx != null && conn instanceof HttpsURLConnection) ((HttpsURLConnection) conn).setSSLSocketFactory(ctx.getSocketFactory());
        conn.setRequestMethod(method);
        if (body != null) {
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes(UTF_8));
        }
        conn.connect();
        InputStream in = conn.getInputStream();
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
}
