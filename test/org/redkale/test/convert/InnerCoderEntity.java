/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.util.concurrent.atomic.*;
import org.redkale.convert.*;
import org.redkale.convert.json.*;

/**
 *
 * @author zhangjx
 */
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
     * 该方法提供给Convert组件自动加载。
     * 1) 方法名可以随意。
     * 2) 方法必须是static
     * 3）方法的参数有且只能有一个， 且必须是org.redkale.convert.Factory。
     * 4）方法的返回类型必须是org.redkale.convert.Decodeable/org.redkale.convert.Encodeable/org.redkale.convert.SimpledCoder
     * 若返回类型不是org.redkale.convert.SimpledCoder, 就必须提供两个方法： 一个返回Decodeable 一个返回 Encodeable。
     *
     * @param factory
     * @return
     */
    private static SimpledCoder<Reader, Writer, InnerCoderEntity> createConvertCoder(final Factory factory) {
        return new SimpledCoder<Reader, Writer, InnerCoderEntity>() {

            //必须与EnMember[] 顺序一致
            private final DeMember[] deMembers = new DeMember[]{DeMember.create(factory, InnerCoderEntity.class, "id"), DeMember.create(factory, InnerCoderEntity.class, "val")};

            //必须与DeMember[] 顺序一致
            private final EnMember[] enMembers = new EnMember[]{EnMember.create(factory, InnerCoderEntity.class, "id"), EnMember.create(factory, InnerCoderEntity.class, "val")};

            @Override
            public void convertTo(Writer out, InnerCoderEntity value) {
                if (value == null) {
                    out.wirteClassName(null);
                    out.writeNull();
                    return;
                }
                out.writeObjectB(enMembers.length, value);
                boolean comma = false;
                for (EnMember member : enMembers) {
                    comma = member.write(out, comma, value);
                }
                out.writeObjectE(value);
            }

            @Override
            public InnerCoderEntity convertFrom(Reader in) {
                if (in.readObjectB() == Reader.SIGN_NULL) return null;
                final AtomicInteger index = new AtomicInteger();
                final Object[] params = new Object[deMembers.length];
                while (in.hasNext()) {
                    DeMember member = in.readField(index, deMembers);
                    in.skipBlank(); //跳过冒号:
                    if (member == null) {
                        in.skipValue(); //跳过该属性的值, 一般不会发生
                    } else {
                        params[index.get()] = member.read(in);
                    }
                    index.incrementAndGet();
                }
                in.readObjectE();
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
        return JsonFactory.root().getConvert().convertTo(this);
    }

    public static void main(String[] args) throws Exception {
        InnerCoderEntity record = InnerCoderEntity.create(200, "haha");
        final JsonConvert convert = JsonFactory.root().getConvert();
        String json = convert.convertTo(record);
        System.out.println(json);
        System.out.println(convert.convertFrom(InnerCoderEntity.class, json).toString());
    }

}
