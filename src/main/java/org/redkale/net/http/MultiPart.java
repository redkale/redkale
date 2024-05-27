/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class MultiPart {

    private final String fileName;

    private final String name;

    private final String contentType;

    private final InputStream in;

    private final LongAdder received;

    MultiPart(String fileName, String name, String contentType, LongAdder received, InputStream in) {
        this.fileName = fileName;
        this.name = name;
        this.in = in;
        this.contentType = contentType;
        this.received = received;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + "name=" + name + ", fileName=" + fileName + ", contentType="
                + contentType + ", received=" + received + '}';
    }

    public boolean save(File file) throws IOException {
        return save(Long.MAX_VALUE, file);
    }

    public boolean save(long max, File file) throws IOException {
        OutputStream out = new FileOutputStream(file);
        boolean rs = save(max, out);
        out.close();
        return rs;
    }

    public byte[] getContentBytes() throws IOException {
        return getContentBytes(Long.MAX_VALUE);
    }

    /**
     * 将文件流读进bytes， 如果超出max指定的值则返回null
     *
     * @param max 最大长度限制
     * @return 内容
     * @throws IOException 异常
     */
    public byte[] getContentBytes(long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return save(max, out) ? out.toByteArray() : null;
    }

    public boolean save(OutputStream out) throws IOException {
        return save(Long.MAX_VALUE, out);
    }

    /**
     * 将文件流写进out， 如果超出max指定的值则中断并返回false
     *
     * @param max 最大长度限制
     * @param out 输出流
     * @return 是否成功
     * @throws IOException 异常
     */
    public boolean save(long max, OutputStream out) throws IOException {
        byte[] bytes = new byte[4096];
        int pos;
        InputStream in0 = this.getInputStream();
        while ((pos = in0.read(bytes)) != -1) {
            if (max < 0) {
                return false;
            }
            out.write(bytes, 0, pos);
            max -= pos;
        }
        return true;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * @see #getFileName()
     * @return String
     * @deprecated replace by {@link #getFileName() }
     */
    @Deprecated(since = "2.8.0")
    public String getFilename() {
        return getFileName();
    }

    public String getName() {
        return name;
    }

    public InputStream getInputStream() {
        return in;
    }

    public long getReceived() {
        return received.longValue();
    }

    public void skip() throws IOException {
        in.skip(Long.MAX_VALUE);
    }
}
