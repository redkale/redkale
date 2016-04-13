/*
 * To change this license header, choose License Headers input Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template input the editor.
 */
package org.redkale.net.http;

import org.redkale.util.ByteArray;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import java.util.regex.*;
import org.redkale.util.AnyValue.DefaultAnyValue;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
public final class MultiContext {

    private static final Logger logger = Logger.getLogger(MultiContext.class.getSimpleName());

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final String contentType;

    private final InputStream in;

    private final Charset charset;

    private final String boundary;

    private final byte[] endboundarray;

    private final ByteArray buf = new ByteArray(64);

    private final DefaultAnyValue parameters;

    private final Pattern fielnamePattern;

    private static final Iterable<MultiPart> emptyIterable = () -> new Iterator<MultiPart>() {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public MultiPart next() {
            return null;
        }
    };

    public MultiContext(final Charset charsetName, final String contentType, final DefaultAnyValue params, final InputStream in, String fielnameRegex) {
        this.charset = charsetName == null ? UTF8 : charsetName;
        this.contentType = contentType.trim();
        this.parameters = params;
        this.boundary = parseBoundary(this.contentType);
        this.endboundarray = ("--" + this.boundary + "--").getBytes();
        this.in = in instanceof BufferedInputStream ? in : new BufferedInputStream(in);
        this.fielnamePattern = fielnameRegex == null || fielnameRegex.isEmpty() ? null : Pattern.compile(fielnameRegex);
    }

    private String parseBoundary(String contentType) {
        if (!contentType.startsWith("multipart/")) {
            return null;
        }
        for (String str : contentType.split(";")) {
            int pos = str.indexOf("boundary=");
            if (pos >= 0) return str.substring(pos + "boundary=".length()).trim();
        }
        return null;
    }

    public boolean isMultipart() {
        return this.boundary != null;
    }

    public Iterable<MultiPart> parts() throws IOException {
        if (!isMultipart()) return emptyIterable;
        final String boundarystr = "--" + this.boundary;
        final Pattern fielnameReg = this.fielnamePattern;
        final String endboundary = boundarystr + "--";
        final byte[] boundarray = ("\n" + boundarystr).getBytes();
        final byte[] buffer = new byte[boundarray.length];
        final InputStream input = this.in;
        final DefaultAnyValue params = this.parameters;
        final AtomicBoolean finaled = new AtomicBoolean(false);
        return () -> new Iterator<MultiPart>() {

            private String boundaryline;

            private MultiPart lastentry;

            @Override
            public boolean hasNext() {
                try {
                    if (lastentry != null) {
                        lastentry.skip();
                        if (finaled.get()) return false;
                    }
                    if (boundaryline == null) boundaryline = readBoundary();
                    //if (debug) System.out.print("boundaryline=" + boundaryline + "  ");
                    if (endboundary.equals(boundaryline) || !boundarystr.equals(boundaryline)) { //结尾或异常
                        lastentry = null;
                        return false;
                    }
                    final String disposition = readLine();
                    //if (debug) System.out.println("disposition=" + disposition);
                    if (disposition.contains("; filename=\"")) { //是上传文件
                        String contentType = readLine();
                        //if (debug) System.out.println("file.contentType=" + contentType);
                        contentType = contentType.substring(contentType.indexOf(':') + 1).trim();
                        readLine(); //读掉空白行
                        String name = parseValue(disposition, "name");
                        String filename = parseValue(disposition, "filename");
                        if (filename == null || filename.isEmpty()) { //没有上传
                            readLine(); //读掉空白行
                            this.boundaryline = null;
                            this.lastentry = null;
                            return this.hasNext();
                        } else {
                            int p1 = filename.lastIndexOf('/');
                            if (p1 < 0) p1 = filename.lastIndexOf('\\');
                            if (p1 >= 0) filename = filename.substring(p1 + 1);
                        }
                        final AtomicLong counter = new AtomicLong(0);
                        InputStream source = new InputStream() {

                            private int bufposition = buffer.length;

                            private boolean end;

                            @Override
                            public int read() throws IOException {
                                if (end) return -1;
                                final byte[] buf = buffer;
                                int ch = (this.bufposition < buf.length) ? (buf[this.bufposition++] & 0xff) : input.read();
                                if ((ch == '\r' && readBuffer())) return -1;
                                counter.incrementAndGet();
                                return ch;
                            }

                            private boolean readBuffer() throws IOException {
                                final byte[] buf = buffer;
                                final int pos = this.bufposition;
                                int s = 0;
                                for (int i = pos; i < buf.length; i++) {
                                    buf[s++] = buf[i];
                                }
                                int readed = 0;
                                while ((readed += input.read(buf, s + readed, pos - readed)) != pos);
                                this.bufposition = 0;
                                if (Arrays.equals(boundarray, buf)) {
                                    this.end = true;
                                    int c1 = input.read();
                                    int c2 = input.read();
                                    finaled.set(c1 == '-' && c2 == '-');
                                    return true;
                                }
                                return false;
                            }

                            @Override
                            public long skip(long count) throws IOException {
                                if (end) return -1;
                                if (count <= 0) return 0;
                                long s = 0;
                                while (read() != -1) {
                                    s++;
                                    if (--count <= 0) break;
                                }
                                return s;
                            }
                        };
                        this.lastentry = new MultiPart(filename, name, contentType, counter, source);
                        if (fielnameReg != null && !fielnameReg.matcher(filename).matches()) {
                            return this.hasNext();
                        }
                        return true;
                    } else { //不是文件
                        readLine(); //读掉空白
                        params.addValue(parseValue(disposition, "name"), readLine());
                        this.boundaryline = null;
                        this.lastentry = null;
                        return this.hasNext();
                    }
                } catch (IOException ex) {
                    logger.log(Level.FINER, "list multiparts abort", ex);
                    return false;
                }
            }

            @Override
            public MultiPart next() {
                return lastentry;
            }

        };
    }

    private String readLine() throws IOException {
        return readLine(false);
    }

    private String readBoundary() throws IOException {
        return readLine(true);
    }

    private String readLine(boolean bd) throws IOException { // bd : 是否是读取boundary
        byte lasted = '\r';
        buf.clear();
        final int bc = this.endboundarray.length;
        int c = 0;
        for (;;) {
            int b = in.read();
            c++;
            if (b == -1 || (lasted == '\r' && b == '\n')) break;
            if (lasted != '\r') buf.write(lasted);
            lasted = (byte) b;
            if (bd && bc == c) {
                buf.write(lasted);
                if (buf.equal(this.endboundarray)) break;
                buf.removeLastByte();
            }
        }
        if (buf.size() == 0) return "";
        return buf.toString(this.charset).trim();
    }

    private static String parseValue(final String str, String name) {
        if (str == null) return null;
        final String key = "; " + name + "=\"";
        int pos = str.indexOf(key);
        if (pos < 0) return null;
        String sub = str.substring(pos + key.length());
        return sub.substring(0, sub.indexOf('"'));
    }

}
