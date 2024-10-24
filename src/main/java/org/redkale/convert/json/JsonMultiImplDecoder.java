/*
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import java.util.*;
import org.redkale.convert.*;
import org.redkale.util.Attribute;

/**
 * 抽象或接口类存在多种实现类的反序列化解析器 <br>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 * @since 2.7.0
 */
public class JsonMultiImplDecoder<T> implements Decodeable<JsonReader, T> {

    protected final JsonFactory factory;

    protected final Class[] types;

    protected final ObjectDecoder[] decoders;

    protected final int maxMemberCount;

    protected final ObjectDecoder firstDecoder;

    protected final Map<String, ObjectDecoder> repeatFieldToDecoders = new HashMap<>();

    protected final Map<String, ObjectDecoder> uniqueFieldToDecoders = new HashMap<>();

    public JsonMultiImplDecoder(final JsonFactory factory, final Class[] types) {
        this.factory = factory;
        this.types = types;
        this.decoders = new ObjectDecoder[types.length];
        int max = 0;
        Set<String>[] fields = new Set[types.length];
        Set<String>[] movsets = new Set[types.length];
        Map<String, Attribute> fieldTypes = new HashMap<>();
        for (int i = 0; i < types.length; i++) {
            movsets[i] = new HashSet();
            fields[i] = new HashSet<>();
            ObjectDecoder decoder = (ObjectDecoder) factory.loadDecoder(types[i]);
            if (decoder.getMembers().length > max) {
                max = decoder.getMembers().length;
            }
            for (DeMember member : decoder.getMembers()) {
                String name = member.getFieldName();
                this.repeatFieldToDecoders.put(name, decoder);
                fields[i].add(name);
                Attribute t = fieldTypes.get(name);
                if (t == null) {
                    fieldTypes.put(name, member.getAttribute());
                } else if (!member.getAttribute().genericType().equals(t.genericType())) {
                    throw new ConvertException("Field(" + name + ")'s Type is not same in "
                            + member.getAttribute().declaringClass() + " and " + t.declaringClass());
                }
            }
            this.decoders[i] = decoder;
        }
        this.maxMemberCount = max;

        for (int i = 0; i < fields.length; i++) {
            Set<String> removes = movsets[i];
            for (String s : fields[i]) {
                boolean repeat = false;
                for (int j = 0; j < fields.length; j++) {
                    if (j == i) {
                        continue;
                    }
                    if (fields[j].contains(s)) {
                        repeat = true;
                        break;
                    }
                }
                if (repeat) {
                    removes.add(s);
                }
            }
        }

        int min = max + 1;
        ObjectDecoder first = null; // 字段最少的类作为默认反解析器
        for (int i = 0; i < fields.length; i++) {
            Set<String> fieldSet = fields[i];
            for (String s : movsets[i]) {
                fieldSet.remove(s); // 移除重复的字段
            }
            if (fieldSet.size() < min) {
                first = this.decoders[i];
                min = fieldSet.size();
            }
            for (String s : fieldSet) {
                this.uniqueFieldToDecoders.put(s, this.decoders[i]);
                this.repeatFieldToDecoders.remove(s);
            }
        }
        this.firstDecoder = first;
    }

    @Override
    public T convertFrom(JsonReader in) {
        if (!in.readObjectB(this)) {
            return null;
        }
        ObjectDecoder decoder = this.firstDecoder;
        Map<String, ObjectDecoder> uniques = this.uniqueFieldToDecoders;
        Map<String, ObjectDecoder> repeats = this.repeatFieldToDecoders;
        int index = -1;
        boolean finaled = false;
        final Object[][] params = new Object[this.maxMemberCount][2];
        while (in.hasNext()) {
            String fieldName = in.readFieldName();
            DeMember member = decoder.getMemberInfo().getMemberByField(fieldName);
            // new Set[]{Utility.ofSet("1", "2", "3"), Utility.ofSet("2", "3"), Utility.ofSet("4", "2", "3"),
            // Utility.ofSet("6", "7", "8"), Utility.ofSet("6", "9")};
            if (member == null && !finaled) {
                ObjectDecoder de = uniques.get(fieldName);
                if (de == null) {
                    de = repeats.get(fieldName);
                    if (de != null) {
                        decoder = de;
                        member = de.getMemberInfo().getMemberByField(fieldName);
                        for (int i = 0; i <= index; i++) { // 迁移params中的DeMember.Attribute
                            if (params[i] != null) {
                                DeMember dm = de.getMemberInfo().getMemberByField(((Attribute) params[i][0]).field());
                                params[i][0] = dm == null ? null : dm.getAttribute();
                            }
                        }
                    }
                } else {
                    finaled = true;
                    decoder = de;
                    member = de.getMemberInfo().getMemberByField(fieldName);
                    for (int i = 0; i <= index; i++) { // 迁移params中的DeMember.Attribute
                        if (params[i] != null) {
                            DeMember dm = de.getMemberInfo().getMemberByField(((Attribute) params[i][0]).field());
                            params[i][0] = dm == null ? null : dm.getAttribute();
                        }
                    }
                }
            }
            in.readColon();
            if (member == null) {
                in.skipValue(); // 跳过不存在的属性的值
            } else {
                params[++index] = new Object[] {member.getAttribute(), member.read(in)};
            }
        }
        in.readObjectE();
        if (decoder.getConstructorMembers() == null) { // 空构造函数
            T result = (T) decoder.getCreator().create();
            for (int i = 0; i <= index; i++) {
                ((Attribute) params[i][0]).set(result, params[i][1]);
            }
            return result;
        } else {
            final DeMember[] constructorFields = decoder.getConstructorMembers();
            final Object[] constructorParams = new Object[constructorFields.length];
            for (int i = 0; i < constructorFields.length; i++) {
                for (int j = 0; j < params.length; j++) {
                    if (params[j] != null
                            && params[j][0] != null
                            && constructorFields[i].getFieldName().equals(((Attribute) params[j][0]).field())) {
                        constructorParams[i] = params[j][1];
                        params[j] = null;
                        break;
                    }
                }
            }
            final T result = (T) decoder.getCreator().create(constructorParams);
            for (int i = 0; i < params.length; i++) {
                if (params[i] != null && params[i][0] != null) {
                    ((Attribute) params[i][0]).set(result, params[i][1]);
                }
            }
            return result;
        }
    }

    @Override
    public Type getType() {
        return Object.class;
    }
}
