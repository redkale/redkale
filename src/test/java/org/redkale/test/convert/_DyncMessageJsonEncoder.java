/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.lang.reflect.Type;
import org.redkale.convert.ObjectEncoder;
import org.redkale.convert.json.*;

/** @author zhangjx */
public class _DyncMessageJsonEncoder extends JsonDynEncoder<Message> {

    protected final byte[] messageFieldBytes = "\"message\":".getBytes();

    protected final byte[] messageCommaFieldBytes = ",\"message\":".getBytes();

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
        boolean comma = false;
        String message = value.getMessage();
        if (message != null) {
            if (comma) {
                out.writeTo(messageCommaFieldBytes);
            } else {
                out.writeTo(messageFieldBytes);
                comma = true;
            }
            out.writeLatin1To(true, message); // out.writeString(message);
        }
        out.writeTo('}');
    }
}
