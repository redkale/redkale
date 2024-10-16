/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.lang.reflect.Type;
import org.redkale.convert.ObjectEncoder;
import org.redkale.convert.json.*;

/** @author zhangjx */
public class _DyncMessageJsonEncoder extends JsonDynEncoder<Message> {

    protected final byte[] messageFieldBytes = "\"message\":".getBytes();

    protected final char[] messageFieldChars = ",\"message\":".toCharArray();

    public _DyncMessageJsonEncoder(JsonFactory factory, Type type, ObjectEncoder objectEncoderSelf) {
        super(factory, type, objectEncoderSelf);
    }

    @Override
    public void convertTo(JsonWriter out, Message value) {
        if (value == null) {
            out.writeObjectNull(null);
            return;
        }
        if (!out.isExtFuncEmpty()) {
            objectEncoderSelf.convertTo(out, value);
            return;
        }
        out.writeTo('{');
        if (out.charsMode()) {
            out.writeFieldStandardStringValue(messageFieldChars, false, value.getMessage());
        } else {
            out.writeFieldStandardStringValue(messageFieldBytes, false, value.getMessage());
        }
        out.writeTo('}');
    }
}
