/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.json;

import java.lang.reflect.Type;
import org.redkale.convert.Encodeable;
import org.redkale.convert.ObjectEncoder;
import org.redkale.convert.json.JsonDynEncoder;
import org.redkale.convert.json.JsonFactory;
import org.redkale.convert.json.JsonWriter;
import org.redkale.test.convert.User;

/**
 *
 * @author zhangjx
 */
public class _DyncUserJsonEncoder extends JsonDynEncoder<User> {
    protected final byte[] ageFieldBytes = "\"age\":".getBytes();
    protected final char[] ageFieldChars = "\"age\":".toCharArray();
    protected final byte[] createTimeFieldBytes = "\"createTime\":".getBytes();
    protected final char[] createTimeFieldChars = "\"createTime\":".toCharArray();
    protected Encodeable createTimeEncoder;
    protected final byte[] idFieldBytes = "\"id\":".getBytes();
    protected final char[] idFieldChars = "\"id\":".toCharArray();
    protected final byte[] nameFieldBytes = "\"name\":".getBytes();
    protected final char[] nameFieldChars = "\"name\":".toCharArray();
    protected final byte[] sexFieldBytes = "\"sex\":".getBytes();
    protected final char[] sexFieldChars = "\"sex\":".toCharArray();
    protected final byte[] nickNameFieldBytes = "\"nickName\":".getBytes();
    protected final char[] nickNameFieldChars = "\"nickName\":".toCharArray();

    public _DyncUserJsonEncoder(JsonFactory factory, Type type, ObjectEncoder objectEncoderSelf) {
        super(factory, type, objectEncoderSelf);
    }

    @Override
    public void convertTo(JsonWriter out, User value) {
        if (value == null) {
            out.writeObjectNull((Class) null);
            return;
        } else if (!out.isExtFuncEmpty()) {
            this.objectEncoderSelf.convertTo(out, value);
            return;
        }
        out.writeTo((byte) '{');
        boolean comma = false;
        if (out.charsMode()) {
            comma = out.writeFieldIntValue(ageFieldChars, comma, value.getAge());
            comma = out.writeFieldObjectValue(
                    createTimeFieldChars, comma, this.createTimeEncoder, value.getCreateTime());
            comma = out.writeFieldLongValue(idFieldChars, comma, value.getId());
            comma = out.writeFieldStringValue(nameFieldChars, comma, value.getName());
            comma = out.writeFieldStringValue(sexFieldChars, comma, value.getSex());
            out.writeFieldStringValue(nickNameFieldChars, comma, value.getNickName());
        } else {
            comma = out.writeFieldIntValue(ageFieldBytes, comma, value.getAge());
            comma = out.writeFieldObjectValue(
                    createTimeFieldBytes, comma, this.createTimeEncoder, value.getCreateTime());
            comma = out.writeFieldLongValue(idFieldBytes, comma, value.getId());
            comma = out.writeFieldStringValue(nameFieldBytes, comma, value.getName());
            comma = out.writeFieldStringValue(sexFieldBytes, comma, value.getSex());
            out.writeFieldStringValue(nickNameFieldBytes, comma, value.getNickName());
        }
        out.writeTo((byte) '}');
    }
}
