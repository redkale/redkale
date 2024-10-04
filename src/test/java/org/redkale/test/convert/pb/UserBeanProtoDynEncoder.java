/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.EnMember;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.pb.ProtobufDynEncoder;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.convert.pb.ProtobufObjectEncoder;
import org.redkale.convert.pb.ProtobufWriter;

/**
 *
 * @author zhangjx
 */
public class UserBeanProtoDynEncoder extends ProtobufDynEncoder<UserBean> {
    protected SimpledCoder numberSimpledCoder;
    protected SimpledCoder scaleSimpledCoder;
    protected EnMember mapEnMember;

    public UserBeanProtoDynEncoder(ProtobufFactory factory, Type type, ProtobufObjectEncoder objectEncoder) {
        super(factory, type, objectEncoder);
    }

    @Override
    public void convertTo(ProtobufWriter out, EnMember parentMember, UserBean value) {
        if (value == null) {
            return;
        }
        ProtobufWriter subout = acceptWriter(out, parentMember, value);
        subout.writeObjectB(value);
        subout.writeFieldValue(1, value.getSeqid());
        subout.writeFieldValue(2, value.getName());
        subout.writeFieldValue(3, value.getImg());
        subout.writeFieldValue(4, numberSimpledCoder, value.getNumber());
        subout.writeFieldValue(5, scaleSimpledCoder, value.getScale());
        subout.writeFieldValue(6, value.getBit());

        subout.writeFieldValue(7, value.isFlag());
        subout.writeFieldValue(8, value.getStatus());
        subout.writeFieldValue(9, value.getId());
        subout.writeFieldValue(10, value.getCreateTime());
        subout.writeFieldValue(11, value.getPoint());
        subout.writeFieldValue(12, value.getMoney());

        subout.writeFieldValue(13, value.getFlag2());
        subout.writeFieldValue(14, value.getStatus2());
        subout.writeFieldValue(15, value.getId2());
        subout.writeFieldValue(16, value.getCreateTime2());
        subout.writeFieldValue(17, value.getPoint2());
        subout.writeFieldValue(18, value.getMoney2());

        subout.writeFieldValue(19, value.id3);
        subout.writeFieldValue(20, value.createTime3);
        subout.writeFieldValue(21, value.point3);
        subout.writeFieldValue(22, value.money3);
        subout.writeFieldValue(23, value.bit3);

        subout.writeFieldValue(19, value.getId4());
        subout.writeFieldValue(20, value.getCreateTime4());
        subout.writeFieldValue(21, value.getPoint4());
        subout.writeFieldValue(22, value.getMoney4());
        subout.writeFieldValue(23, value.getBit4());

        subout.writeFieldValue(19, value.getId5());
        subout.writeFieldValue(20, value.getCreateTime5());
        subout.writeFieldValue(21, value.getPoint5());
        subout.writeFieldValue(22, value.getMoney5());
        subout.writeFieldValue(23, value.getBit5());

        subout.writeFieldIntsValue(19, value.getId6());
        subout.writeFieldLongsValue(20, value.getCreateTime6());
        subout.writeFieldFloatsValue(21, value.getPoint6());
        subout.writeFieldDoublesValue(22, value.getMoney6());
        subout.writeFieldBytesValue(23, value.getBit6());
        subout.writeFieldStringsValue(23, value.getStrs());

        subout.writeObjectField(mapEnMember, value);
        subout.writeObjectE(value);
        offerWriter(out, subout);
    }
}
