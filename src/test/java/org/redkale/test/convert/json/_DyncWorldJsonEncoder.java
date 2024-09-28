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
public class _DyncWorldJsonEncoder extends JsonDynEncoder<World> {

    protected final byte[] idFieldBytes = "\"id\":".getBytes();

    protected final byte[] randomNumberFieldBytes = ",\"randomNumber\":".getBytes();

    public _DyncWorldJsonEncoder(JsonFactory factory, Type type, ObjectEncoder objectEncoderSelf) {
        super(factory, type, objectEncoderSelf);
    }

    @Override
    public void convertTo(JsonWriter out, World value) {
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

        out.writeTo(idFieldBytes);
        out.writeInt(value.getId());

        out.writeTo(randomNumberFieldBytes);
        out.writeInt(value.getRandomNumber());

        out.writeTo('}');
    }
}
