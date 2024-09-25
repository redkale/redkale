/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.EnMember;
import org.redkale.convert.ObjectEncoder;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.pb.ProtobufDynEncoder;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.convert.pb.ProtobufWriter;

/**
 *
 * @author zhangjx
 */
public class UserBeanProtoDynEncoder extends ProtobufDynEncoder<UserBean> {
    protected SimpledCoder numberSimpledCoder;
    protected SimpledCoder scaleSimpledCoder;
    protected EnMember mapEnMember;

    public UserBeanProtoDynEncoder(ProtobufFactory factory, Type type, ObjectEncoder objectEncoder) {
        super(factory, type, objectEncoder);
    }

    @Override
    public void convertTo(ProtobufWriter out0, UserBean value) {
        if (value == null) {
            return;
        }
        ProtobufWriter out = objectWriter(out0, value);
        out.writeObjectB(value);
        out.writeFieldValue(1, value.getSeqid());
        out.writeFieldValue(2, value.getName());
        out.writeFieldValue(3, value.getImg());
        out.writeFieldValue(4, numberSimpledCoder, value.getNumber());
        out.writeFieldValue(5, scaleSimpledCoder, value.getScale());
        out.writeFieldValue(6, value.getBit());

        out.writeFieldValue(7, value.isFlag());
        out.writeFieldValue(8, value.getStatus());
        out.writeFieldValue(9, value.getId());
        out.writeFieldValue(10, value.getCreateTime());
        out.writeFieldValue(11, value.getPoint());
        out.writeFieldValue(12, value.getMoney());

        out.writeFieldValue(13, value.getFlag2());
        out.writeFieldValue(14, value.getStatus2());
        out.writeFieldValue(15, value.getId2());
        out.writeFieldValue(16, value.getCreateTime2());
        out.writeFieldValue(17, value.getPoint2());
        out.writeFieldValue(18, value.getMoney2());

        out.writeFieldValue(19, value.id3);
        out.writeFieldValue(20, value.createTime3);
        out.writeFieldValue(21, value.point3);
        out.writeFieldValue(22, value.money3);
        out.writeFieldValue(23, value.bit3);

        out.writeFieldValue(19, value.getId4());
        out.writeFieldValue(20, value.getCreateTime4());
        out.writeFieldValue(21, value.getPoint4());
        out.writeFieldValue(22, value.getMoney4());
        out.writeFieldValue(23, value.getBit4());

        out.writeFieldValue(19, value.getId5());
        out.writeFieldValue(20, value.getCreateTime5());
        out.writeFieldValue(21, value.getPoint5());
        out.writeFieldValue(22, value.getMoney5());
        out.writeFieldValue(23, value.getBit5());

        out.writeFieldIntsValue(19, value.getId6());
        out.writeFieldLongsValue(20, value.getCreateTime6());
        out.writeFieldFloatsValue(21, value.getPoint6());
        out.writeFieldDoublesValue(22, value.getMoney6());
        out.writeFieldBytesValue(23, value.getBit6());

        out.writeFieldValue(100, value.kind);

        out.writeObjectField(mapEnMember, value);
        out.writeObjectE(out);
    }
}
