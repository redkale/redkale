/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.net.*;
import java.util.Arrays;
import java.util.Objects;
import org.redkale.convert.*;
import org.redkale.util.Utility;

/**
 * InetAddress 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
@SuppressWarnings("unchecked")
public class InetAddressSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, InetAddress> {

    public static final InetAddressSimpledCoder instance = new InetAddressSimpledCoder();

    protected final SimpledCoder<R, W, byte[]> bsSimpledCoder;

    protected InetAddressSimpledCoder() {
        this.bsSimpledCoder = ByteArraySimpledCoder.instance;
    }

    public InetAddressSimpledCoder(SimpledCoder<R, W, byte[]> bSimpledCoder) {
        this.bsSimpledCoder = Objects.requireNonNull(bSimpledCoder);
    }

    @Override
    public void convertTo(W out, InetAddress value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        bsSimpledCoder.convertTo(out, value.getAddress());
    }

    @Override
    public InetAddress convertFrom(R in) {
        byte[] bytes = bsSimpledCoder.convertFrom(in);
        if (bytes == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * InetSocketAddress 的SimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    @SuppressWarnings("unchecked")
    public static class InetSocketAddressSimpledCoder<R extends Reader, W extends Writer>
            extends SimpledCoder<R, W, InetSocketAddress> {

        public static final InetSocketAddressSimpledCoder instance = new InetSocketAddressSimpledCoder();

        protected final SimpledCoder<R, W, byte[]> bsSimpledCoder;

        protected InetSocketAddressSimpledCoder() {
            this.bsSimpledCoder = ByteArraySimpledCoder.instance;
        }

        public InetSocketAddressSimpledCoder(SimpledCoder<R, W, byte[]> bSimpledCoder) {
            this.bsSimpledCoder = Objects.requireNonNull(bSimpledCoder);
        }

        @Override
        public void convertTo(W out, InetSocketAddress value) {
            if (value == null) {
                out.writeNull();
                return;
            }
            int port = value.getPort();
            byte[] bs = value.getAddress().getAddress();
            bs = Utility.append(bs, (byte) ((port & 0xFF00) >> 8), (byte) (port & 0xFF));
            bsSimpledCoder.convertTo(out, bs);
        }

        @Override
        public InetSocketAddress convertFrom(R in) {
            byte[] bytes = bsSimpledCoder.convertFrom(in);
            if (bytes == null) {
                return null;
            }
            byte[] addr = Arrays.copyOf(bytes, bytes.length - 2);
            int port = ((0xff00 & (bytes[bytes.length - 2] << 8)) | (0xff & bytes[bytes.length - 1]));
            try {
                return new InetSocketAddress(InetAddress.getByAddress(addr), port);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
