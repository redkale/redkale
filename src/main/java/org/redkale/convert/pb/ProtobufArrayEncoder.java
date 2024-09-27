/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 * 非基本类型数组序列化。 注意: 基础类型不能使用此类
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufArrayEncoder<T> extends ArrayEncoder<ProtobufWriter, T> {

    public ProtobufArrayEncoder(ProtobufFactory factory, Type type) {
        super(factory, type);
    }

    @Override
    protected void writeMemberValue(ProtobufWriter out, EnMember member, Encodeable encoder, Object item, int index) {
        if (member != null) {
            out.writeFieldName(member);
        }
        if (item == null) {
            out.writeUInt32(0);
        } else if (item instanceof CharSequence) {
            encoder.convertTo(out, item);
        } else {
            ProtobufWriter tmp = out.pollChild();
            encoder.convertTo(tmp, item);
            out.writeLength(tmp.count());
            out.writeTo(tmp.toArray());
            out.offerChild(tmp);
        }
    }
}
