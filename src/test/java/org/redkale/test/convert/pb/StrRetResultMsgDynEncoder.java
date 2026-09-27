/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.EnMember;
import org.redkale.convert.pb.ProtobufDynEncoder;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.convert.pb.ProtobufObjectEncoder;
import org.redkale.convert.pb.ProtobufWriter;
import org.redkale.test.convert.pb.PBCustMessage2Test.StringRetResultMessage;

/**
 *
 * @author zhangjx
 */
public class StrRetResultMsgDynEncoder extends ProtobufDynEncoder<StringRetResultMessage> {

    public StrRetResultMsgDynEncoder(ProtobufFactory factory, Type type, ProtobufObjectEncoder objectEncoder) {
        super(factory, type, objectEncoder);
    }

    @Override
    public void convertTo(ProtobufWriter out, EnMember parentMember, StringRetResultMessage value) {
        if (value == null) {
            return;
        }
        ProtobufWriter subout = acceptWriter(out, parentMember, value);
        subout.writeObjectB(value);
        subout.writeFieldValue(1, value.isSuccess());
        subout.writeFieldValue(2, value.getRetcode());
        subout.writeFieldValue(3, value.getRetinfo());
        subout.writeFieldValue(4, value.getResult());

        subout.writeObjectE(value);
        offerWriter(out, subout);
    }
}
