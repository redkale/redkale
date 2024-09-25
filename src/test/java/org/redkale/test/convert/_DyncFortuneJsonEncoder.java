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
public class _DyncFortuneJsonEncoder extends JsonDynEncoder<Fortune> {

    protected final byte[] idFieldBytes = "\"id\":".getBytes();

    protected final byte[] messageCommaFieldBytes = ",\"message\":".getBytes();

    public _DyncFortuneJsonEncoder(JsonFactory factory, Type type, ObjectEncoder objectEncoderSelf) {
        super(factory, type, objectEncoderSelf);
    }

    @Override
    public void convertTo(JsonWriter out, Fortune value) {
        if (value == null) {
            out.writeObjectNull(null);
            return;
        }
        if (!out.isExtFuncEmpty()) {
            objectEncoderSelf.convertTo(out, value);
            return;
        }

        out.writeTo('{');

        out.writeTo(idFieldBytes);
        out.writeInt(value.getId());

        String message = value.getMessage();
        if (message != null) {
            out.writeTo(messageCommaFieldBytes);
            out.writeString(message);
        }

        out.writeTo('}');
    }
}
