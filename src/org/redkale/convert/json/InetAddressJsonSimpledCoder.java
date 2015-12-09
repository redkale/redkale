/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.net.*;
import org.redkale.convert.*;
import org.redkale.convert.ext.*;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class InetAddressJsonSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<JsonReader, JsonWriter, InetAddress> {

    public static final InetAddressJsonSimpledCoder instance = new InetAddressJsonSimpledCoder();

    @Override
    public void convertTo(JsonWriter out, InetAddress value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        StringSimpledCoder.instance.convertTo(out, value.getHostAddress());
    }

    @Override
    public InetAddress convertFrom(JsonReader in) {
        String str = StringSimpledCoder.instance.convertFrom(in);
        if (str == null) return null;
        try {
            return InetAddress.getByName(str);
        } catch (Exception ex) {
            return null;
        }
    }

    public final static class InetSocketAddressJsonSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<JsonReader, JsonWriter, InetSocketAddress> {

        public static final InetSocketAddressJsonSimpledCoder instance = new InetSocketAddressJsonSimpledCoder();

        @Override
        public void convertTo(JsonWriter out, InetSocketAddress value) {
            if (value == null) {
                out.writeNull();
                return;
            }
            StringSimpledCoder.instance.convertTo(out, value.getHostString() + ":" + value.getPort());
        }

        @Override
        public InetSocketAddress convertFrom(JsonReader in) {
            String str = StringSimpledCoder.instance.convertFrom(in);
            if (str == null) return null;
            try {
                int pos = str.indexOf(':');
                return new InetSocketAddress(str.substring(0, pos), Integer.parseInt(str.substring(pos + 1)));
            } catch (Exception ex) {
                return null;
            }
        }

    }
}
