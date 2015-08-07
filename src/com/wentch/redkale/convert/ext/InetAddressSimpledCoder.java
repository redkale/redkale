/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.ext;

import com.wentch.redkale.convert.SimpledCoder;
import com.wentch.redkale.convert.Writer;
import com.wentch.redkale.convert.Reader;
import java.net.*;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class InetAddressSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, InetAddress> {

    public static final InetAddressSimpledCoder instance = new InetAddressSimpledCoder();

    @Override
    public void convertTo(W out, InetAddress value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        ByteArraySimpledCoder.instance.convertTo(out, value.getAddress());
    }

    @Override
    public InetAddress convertFrom(R in) {
        byte[] bytes = ByteArraySimpledCoder.instance.convertFrom(in);
        if (bytes == null) return null;
        try {
            return InetAddress.getByAddress(bytes);
        } catch (Exception ex) {
            return null;
        }
    }

    public final static class InetSocketAddressSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, InetSocketAddress> {

        public static final InetSocketAddressSimpledCoder instance = new InetSocketAddressSimpledCoder();

        @Override
        public void convertTo(W out, InetSocketAddress value) {
            if (value == null) {
                out.writeNull();
                return;
            }
            ByteArraySimpledCoder.instance.convertTo(out, value.getAddress().getAddress());
            out.writeInt(value.getPort());
        }

        @Override
        public InetSocketAddress convertFrom(R in) {
            byte[] bytes = ByteArraySimpledCoder.instance.convertFrom(in);
            if (bytes == null) return null;
            int port = in.readInt();
            try {
                return new InetSocketAddress(InetAddress.getByAddress(bytes), port);
            } catch (Exception ex) {
                return null;
            }
        }

    }
}
