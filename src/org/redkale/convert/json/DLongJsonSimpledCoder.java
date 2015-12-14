/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class DLongJsonSimpledCoder extends JsonSimpledCoder<DLong> {

    public static final DLongJsonSimpledCoder instance = new DLongJsonSimpledCoder();

    @Override
    public void convertTo(final JsonWriter out, final DLong value) {
        if (value == null) {
            out.writeNull();
        } else {
            out.writeSmallString(value.toString());
        }
    }

    @Override
    public DLong convertFrom(JsonReader in) {
        final String str = in.readString();
        if (str == null) return null;
        return new DLong(Utility.hexToBin(str));
    }
}
