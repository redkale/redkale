/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import javax.net.ssl.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public final class Utility {

    private static final int zoneRawOffset = TimeZone.getDefault().getRawOffset();

    private static final String format = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final char hex[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static final javax.net.ssl.SSLContext DEFAULTSSL_CONTEXT;

    static {

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

    public static String now() {
        return String.format(format, System.currentTimeMillis());
    }

    public static void println(String string, ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) return;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.flip();
        println(string, bytes);
    }

    public static void println(String string, byte... bytes) {
        if (bytes == null) return;
        StringBuilder sb = new StringBuilder();
        if (string != null) sb.append(string);
        sb.append(bytes.length).append(".[");
        boolean last = false;
        for (byte b : bytes) {
            if (last) sb.append(',');
            int v = b & 0xff;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v));
            last = true;
        }
        sb.append(']');
        (System.out).println(sb);
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

    /**
     * 获取当天凌晨零点的格林时间
     *
     * @return
     */
    public static long midnight() {
        return midnight(System.currentTimeMillis());
    }

    /**
     * 获取指定时间当天凌晨零点的格林时间
     *
     * @param time
     * @return
     */
    public static long midnight(long time) {
        return (time + zoneRawOffset) / 86400000 * 86400000 - zoneRawOffset;
    }

    /**
     * 获取当天20151231格式的int值
     *
     * @return
     */
    public static int today() {
        final Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取时间点所在星期的周一
     *
     * @param time
     * @return
     */
    public static long monday(long time) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return cal.getTimeInMillis();
    }

    /**
     * 获取时间点所在星期的周日
     *
     * @param time
     * @return
     */
    public static long sunday(long time) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        return cal.getTimeInMillis();
    }

    /**
     * 获取时间点所在月份的1号
     *
     * @param time
     * @return
     */
    public static long monthFirstDay(long time) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return cal.getTimeInMillis();
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

    public static byte[] hexToBin(String str) {
        return hexToBin(charArray(str));
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
        return encodeUTF8(value.toCharArray());
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

    public static char[] charArray(String value) { //与JDK 8 的实现版本不一样
        return value == null ? null : value.toCharArray();
    }

    public static char[] charArray(StringBuilder value) { //与JDK 8 的实现版本不一样
        return value == null ? null : value.toString().toCharArray();
    }

    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, final char[] array) {
        return encodeUTF8(buffer, array, 0, array.length);
    }

    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, int bytesLength, final char[] array) {
        return encodeUTF8(buffer, bytesLength, array, 0, array.length);
    }

    public static int encodeUTF8Length(String value) {
        if (value == null) return -1;
        return encodeUTF8Length(value.toCharArray());
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
     * <p>
     * @param high
     * @param low
     * @return
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

    public static Socket createDefaultSSLSocket(InetSocketAddress address) throws IOException {
        return createDefaultSSLSocket(address.getAddress(), address.getPort());
    }

    public static Socket createDefaultSSLSocket(InetAddress host, int port) throws IOException {
        Socket socket = DEFAULTSSL_CONTEXT.getSocketFactory().createSocket(host, port);

        return socket;
    }

    public static String postHttpContent(String url) throws IOException {
        return remoteHttpContent(null, "POST", url, null, null).toString("UTF-8");
    }

    public static String postHttpContent(String url, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, null, body).toString("UTF-8");
    }

    public static String postHttpContent(String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, headers, body).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "POST", url, null, null).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, null, body).toString("UTF-8");
    }

    public static String postHttpContent(SSLContext ctx, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, headers, body).toString("UTF-8");
    }

    public static byte[] postHttpBytesContent(String url) throws IOException {
        return remoteHttpContent(null, "POST", url, null, null).toByteArray();
    }

    public static byte[] postHttpBytesContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "POST", url, null, null).toByteArray();
    }

    public static byte[] postHttpBytesContent(String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "POST", url, headers, body).toByteArray();
    }

    public static byte[] postHttpBytesContent(SSLContext ctx, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "POST", url, headers, body).toByteArray();
    }

    public static String getHttpContent(String url) throws IOException {
        return remoteHttpContent(null, "GET", url, null, null).toString("UTF-8");
    }

    public static String getHttpContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "GET", url, null, null).toString("UTF-8");
    }

    public static String getHttpContent(SSLContext ctx, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "GET", url, headers, body).toString("UTF-8");
    }

    public static String getHttpContent(String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "GET", url, headers, body).toString("UTF-8");
    }

    public static byte[] getHttpBytesContent(String url) throws IOException {
        return remoteHttpContent(null, "GET", url, null, null).toByteArray();
    }

    public static byte[] getHttpBytesContent(SSLContext ctx, String url) throws IOException {
        return remoteHttpContent(ctx, "GET", url, null, null).toByteArray();
    }

    public static byte[] getHttpBytesContent(String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(null, "GET", url, headers, body).toByteArray();
    }

    public static byte[] getHttpBytesContent(SSLContext ctx, String url, Map<String, String> headers, String body) throws IOException {
        return remoteHttpContent(ctx, "GET", url, headers, body).toByteArray();
    }

    protected static ByteArrayOutputStream remoteHttpContent(SSLContext ctx, String method, String url, Map<String, String> headers, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        if (conn instanceof HttpsURLConnection) ((HttpsURLConnection) conn).setSSLSocketFactory((ctx == null ? DEFAULTSSL_CONTEXT : ctx).getSocketFactory());
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
            return remoteHttpContent(ctx, method, newurl, headers, body);
        }
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
