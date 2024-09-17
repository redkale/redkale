/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.util.*;
import org.redkale.convert.*;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;
import org.redkale.util.*;

/** @author zhangjx */
public class InnerCoderEntity {

    private final String val;

    private final int id;

    private InnerCoderEntity(int id, String value) {
        this.id = id;
        this.val = value;
    }

    public static InnerCoderEntity create(int id, String value) {
        return new InnerCoderEntity(id, value);
    }

    /**
     * 该方法提供给Convert组件自动加载。 1) 方法名可以随意。 2) 方法必须是static 3）方法的参数有且只能有一个， 且必须是org.redkale.convert.ConvertFactory或子类。 —3.1)
     * 参数类型为org.redkale.convert.ConvertFactory 表示适合JSON和BSON。 —3.2) 参数类型为org.redkale.convert.json.JsonFactory 表示仅适合JSON。
     * —3.3) 参数类型为org.redkale.convert.bson.BsonFactory 表示仅适合BSON。
     * 4）方法的返回类型必须是org.redkale.convert.Decodeable/org.redkale.convert.Encodeable/org.redkale.convert.SimpledCoder
     * 若返回类型不是org.redkale.convert.SimpledCoder, 就必须提供两个方法： 一个返回Decodeable 一个返回 Encodeable。
     *
     * @param factory
     * @return
     */
    static SimpledCoder<Reader, Writer, InnerCoderEntity> createConvertCoder(
            final org.redkale.convert.ConvertFactory factory) {
        return new SimpledCoder<Reader, Writer, InnerCoderEntity>() {

            private DeMemberNode memberNode;

            private Map<String, DeMember> deMemberFieldMap;

            private Map<Integer, DeMember> deMemberTagMap;

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
                this.deMemberFieldMap = new HashMap<>(this.deMembers.length);
                this.deMemberTagMap = new HashMap<>(this.deMembers.length);
                for (DeMember member : this.deMembers) {
                    this.deMemberFieldMap.put(member.getAttribute().field(), member);
                    this.deMemberTagMap.put(member.getTag(), member);
                }
                this.memberNode = DeMemberNode.create(deMembers);
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
                    DeMember member = in.readFieldName(memberNode, deMemberFieldMap, deMemberTagMap); // 读取字段名
                    in.readBlank(); // 读取字段名与字段值之间的间隔符，JSON则是跳过冒号:
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

    public static void main(String[] args) throws Exception {
        InnerCoderEntity record = InnerCoderEntity.create(200, "haha");
        final JsonConvert convert = JsonConvert.root();
        String json = convert.convertTo(record);
        System.out.println(json);
        System.out.println(convert.convertFrom(InnerCoderEntity.class, json).toString());

        final BsonConvert convert2 = BsonFactory.root().getConvert();
        byte[] bs = convert2.convertTo(InnerCoderEntity.class, null);
        Utility.println("--", bs);
        InnerCoderEntity r = convert2.convertFrom(InnerCoderEntity.class, bs);
        System.out.println(r);
    }
}
