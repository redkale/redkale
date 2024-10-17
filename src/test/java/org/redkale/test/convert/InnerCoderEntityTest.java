/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert;

import org.junit.jupiter.api.Test;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.DeMember;
import org.redkale.convert.DeMemberInfo;
import org.redkale.convert.EnMember;
import org.redkale.convert.Reader;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class InnerCoderEntityTest {
    public static void main(String[] args) throws Throwable {
        InnerCoderEntityTest test = new InnerCoderEntityTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        InnerCoderEntity record = InnerCoderEntity.create(200, "haha");
        final JsonConvert convert = JsonConvert.root();
        String json = convert.convertTo(record);
        System.out.println(json);
        System.out.println(convert.convertFrom(InnerCoderEntity.class, json).toString());

        final ProtobufConvert convert2 = ProtobufFactory.root().getConvert();
        byte[] bs = convert2.convertTo(InnerCoderEntity.class, null);
        Utility.println("--", bs);
        InnerCoderEntity r = convert2.convertFrom(InnerCoderEntity.class, bs);
        System.out.println(r);
    }

    public static class InnerCoderEntity {
        @ConvertColumn(index = 1)
        private final int id;

        @ConvertColumn(index = 2)
        private final String val;

        private InnerCoderEntity(int id, String value) {
            this.id = id;
            this.val = value;
        }

        public static InnerCoderEntity create(int id, String value) {
            return new InnerCoderEntity(id, value);
        }

        /**
         * 该方法提供给Convert组件自动加载。 1) 方法名可以随意。 2) 方法必须是static 3）方法的参数有且只能有一个， 且必须是org.redkale.convert.ConvertFactory或子类。 —3.1)
         * 参数类型为org.redkale.convert.ConvertFactory 表示适合JSON和PROTOBUF。 —3.2) 参数类型为org.redkale.convert.json.JsonFactory 表示仅适合JSON。
         * —3.3) 参数类型为org.redkale.convert.pb.ProtobufFactory 表示仅适合PROTOBUF。
         * 4）方法的返回类型必须是org.redkale.convert.Decodeable/org.redkale.convert.Encodeable/org.redkale.convert.SimpledCoder
         * 若返回类型不是org.redkale.convert.SimpledCoder, 就必须提供两个方法： 一个返回Decodeable 一个返回 Encodeable。
         *
         * @param factory
         * @return
         */
        static SimpledCoder<Reader, Writer, InnerCoderEntity> createConvertCoder(
                final org.redkale.convert.ConvertFactory factory) {
            return new SimpledCoder<Reader, Writer, InnerCoderEntity>() {

                private DeMemberInfo memberInfo;

                // 必须与EnMember[] 顺序一致
                private final DeMember[] deMembers = new DeMember[] {
                    DeMember.create(factory, InnerCoderEntity.class, "id"),
                    DeMember.create(factory, InnerCoderEntity.class, "val")
                };

                // 必须与DeMember[] 顺序一致
                private final EnMember[] enMembers = new EnMember[] {
                    EnMember.create(factory, InnerCoderEntity.class, "id"),
                    EnMember.create(factory, InnerCoderEntity.class, "val")
                };

                {
                    this.memberInfo = DeMemberInfo.create(deMembers);
                }

                @Override
                public void convertTo(Writer out, InnerCoderEntity value) {
                    if (value == null) {
                        out.writeObjectNull(InnerCoderEntity.class);
                        return;
                    }
                    out.writeObjectB(value);
                    for (EnMember member : enMembers) {
                        out.writeObjectField(member, value);
                    }
                    out.writeObjectE(value);
                }

                @Override
                public InnerCoderEntity convertFrom(Reader in) {
                    if (in.readObjectB(InnerCoderEntity.class) == null) return null;
                    int index = 0;
                    final Object[] params = new Object[deMembers.length];
                    while (in.hasNext()) {
                        DeMember member = in.readField(memberInfo); // 读取字段名
                        in.readColon(); // 读取字段名与字段值之间的间隔符，JSON则是跳过冒号:
                        if (member == null) {
                            in.skipValue(); // 跳过不存在的字段的值, 一般不会发生
                        } else {
                            params[index++] = member.read(in);
                        }
                    }
                    in.readObjectE(InnerCoderEntity.class);
                    return InnerCoderEntity.create(params[0] == null ? 0 : (Integer) params[0], (String) params[1]);
                }
            };
        }

        public int getId() {
            return id;
        }

        public String getVal() {
            return val;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
