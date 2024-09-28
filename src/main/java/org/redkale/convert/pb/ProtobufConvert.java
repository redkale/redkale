/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.*;
import org.redkale.annotation.Nullable;
import org.redkale.convert.*;
import org.redkale.convert.ext.StringArraySimpledCoder;
import org.redkale.util.*;

/**
 * protobuf的Convert实现 <br>
 * 注意: <br>
 * 1、 只实现proto3版本 <br>
 * 2、 int统一使用sint32, long统一使用sint64 <br>
 * 3、 集合统一 packed repeated <br>
 * 4、 目前使用的基础数据类型为：bool、sint32、sint64、float、double、bytes、string、map、Any <br>
 *
 * @author zhangjx
 */
public class ProtobufConvert extends BinaryConvert<ProtobufReader, ProtobufWriter> {

    private final ThreadLocal<ProtobufWriter> writerPool = Utility.withInitialThreadLocal(ProtobufWriter::new);

    private final Consumer<ProtobufWriter> writerConsumer = this::offerWriter;

    private final ThreadLocal<ProtobufReader> readerPool = Utility.withInitialThreadLocal(ProtobufReader::new);

    @Nullable
    private Encodeable lastEncodeable;

    @Nullable
    private Decodeable lastDecodeable;

    protected ProtobufConvert(ConvertFactory<ProtobufReader, ProtobufWriter> factory, int features) {
        super(factory, features);
    }

    @Override
    public ProtobufFactory getFactory() {
        return (ProtobufFactory) factory;
    }

    public static ProtobufConvert root() {
        return ProtobufFactory.root().getConvert();
    }

    @Override
    public ProtobufConvert newConvert(final BiFunction<Attribute, Object, Object> objFieldFunc) {
        return newConvert(objFieldFunc, null, null);
    }

    @Override
    public ProtobufConvert newConvert(
            final BiFunction<Attribute, Object, Object> objFieldFunc, BiFunction mapFieldFunc) {
        return newConvert(objFieldFunc, mapFieldFunc, null);
    }

    @Override
    public ProtobufConvert newConvert(
            final BiFunction<Attribute, Object, Object> objFieldFunc, Function<Object, ConvertField[]> objExtFunc) {
        return newConvert(objFieldFunc, null, objExtFunc);
    }

    @Override
    public ProtobufConvert newConvert(
            final BiFunction<Attribute, Object, Object> fieldFunc,
            BiFunction mapFieldFunc,
            Function<Object, ConvertField[]> objExtFunc) {
        return new ProtobufConvert(getFactory(), features) {
            @Override
            protected <S extends ProtobufWriter> S configWrite(S writer) {
                super.configWrite(writer);
                return fieldFunc(writer, fieldFunc, mapFieldFunc, objExtFunc);
            }
        };
    }

    @Override
    public ProtobufReader pollReader() {
        ProtobufReader reader = readerPool.get();
        if (reader == null) {
            reader = new ProtobufReader();
        } else {
            readerPool.set(null);
        }
        return reader;
    }

    @Override
    public void offerReader(final ProtobufReader in) {
        if (in != null) {
            in.recycle();
            readerPool.set(in);
        }
    }

    // ------------------------------ writer -----------------------------------------------------------
    @Override
    protected <S extends ProtobufWriter> S configWrite(S writer) {
        return (S) writer.configWrite();
    }

    public ProtobufByteBufferWriter pollProtobufWriter(final Supplier<ByteBuffer> supplier) {
        return configWrite(new ProtobufByteBufferWriter(features, ((ProtobufFactory) factory).enumtostring, supplier));
    }

    public ProtobufWriter pollProtobufWriter(final OutputStream out) {
        return configWrite(new ProtobufStreamWriter(features, ((ProtobufFactory) factory).enumtostring, out));
    }

    @Override
    public ProtobufWriter pollWriter() {
        ProtobufWriter writer = writerPool.get();
        if (writer == null) {
            writer = new ProtobufWriter();
        } else {
            writerPool.set(null);
        }
        return configWrite(writer.withFeatures(features).enumtostring(((ProtobufFactory) factory).enumtostring));
    }

    @Override
    public void offerWriter(final ProtobufWriter out) {
        if (out != null) {
            out.recycle();
            writerPool.set(out);
        }
    }

    /**
     * 请求参数的类型
     *
     * @param type 请求参数的类型
     * @return String
     */
    public String getJsonDecodeDescriptor(Type type) {
        StringBuilder sb = new StringBuilder();
        defineJsonDecodeDescriptor(null, new ArrayList<>(), type, sb, "", null);
        return sb.toString();
    }

    public String getJsonDecodeDescriptor(Type type, BiFunction<Type, DeMember, Boolean> func) {
        StringBuilder sb = new StringBuilder();
        defineJsonDecodeDescriptor(null, new ArrayList<>(), type, sb, "", func);
        return sb.toString();
    }

    protected String getJsonDecodeDescriptor(
            Type parent, List<String> list, Type type, BiFunction<Type, DeMember, Boolean> func) {
        StringBuilder sb = new StringBuilder();
        defineJsonDecodeDescriptor(parent, list, type, sb, "", func);
        return sb.toString();
    }

    protected void defineJsonDecodeDescriptor(
            Type parent,
            List<String> list,
            Type type,
            StringBuilder sb,
            String prefix,
            BiFunction<Type, DeMember, Boolean> excludeFunc) {
        Decodeable decoder = factory.loadDecoder(type);
        boolean dot = sb.length() > 0;
        if (decoder instanceof ObjectDecoder) {
            if (sb.length() > 0) {
                if (list.contains(parent + "" + defineTypeName(type))) {
                    return;
                }
                list.add(parent + "" + defineTypeName(type));
                sb.append(prefix)
                        .append("\"message ")
                        .append(defineTypeName(type))
                        .append("\" : {\r\n");
            } else {
                sb.append("{\r\n");
            }
            DeMember[] ems = ((ObjectDecoder) decoder).getMembers();
            List<DeMember> members = new ArrayList<>();
            for (DeMember member : ems) {
                if (excludeFunc != null && excludeFunc.apply(type, member)) {
                    continue;
                }
                members.add(member);
            }
            for (DeMember member : members) {
                Type mtype = member.getDecoder().getType();
                if (!(mtype instanceof Class)) {
                    if (mtype instanceof ParameterizedType) {
                        final ParameterizedType pt = (ParameterizedType) mtype;
                        if (pt.getActualTypeArguments().length == 1
                                && (pt.getActualTypeArguments()[0] instanceof Class)) {
                            defineJsonDecodeDescriptor(parent, list, mtype, sb, prefix + "    ", excludeFunc);
                        }
                    } else if (mtype instanceof GenericArrayType) {
                        final GenericArrayType gt = (GenericArrayType) mtype;
                        if (!gt.getGenericComponentType().toString().startsWith("java")
                                && !gt.getGenericComponentType().toString().startsWith("class java")
                                && gt.getGenericComponentType().toString().indexOf('.') > 0) {
                            defineJsonDecodeDescriptor(
                                    parent, list, gt.getGenericComponentType(), sb, prefix + "    ", excludeFunc);
                        }
                    }
                    continue;
                }
                Class mclz = (Class) member.getDecoder().getType();
                if (!mclz.isArray()
                        && !mclz.isEnum()
                        && !mclz.isPrimitive()
                        && !mclz.getName().startsWith("java")) {
                    defineJsonDecodeDescriptor(parent, list, mclz, sb, prefix + "    ", excludeFunc);
                } else if (mclz.isArray()
                        && !mclz.getComponentType().getName().startsWith("java")
                        && !mclz.getComponentType().isPrimitive()
                        && !mclz.getComponentType().isArray()
                        && !mclz.getComponentType().getName().equals("boolean")
                        && !mclz.getComponentType().getName().equals("byte")
                        && !mclz.getComponentType().getName().equals("char")
                        && !mclz.getComponentType().getName().equals("short")
                        && !mclz.getComponentType().getName().equals("int")
                        && !mclz.getComponentType().getName().equals("long")
                        && !mclz.getComponentType().getName().equals("float")
                        && !mclz.getComponentType().getName().equals("double")) {
                    defineJsonDecodeDescriptor(parent, list, mclz.getComponentType(), sb, prefix + "    ", excludeFunc);
                }
            }
            for (int i = 0; i < members.size(); i++) {
                DeMember member = members.get(i);
                try {
                    sb.append(prefix)
                            .append("    \"")
                            .append(ProtobufFactory.wireTypeString(
                                    member.getDecoder().getType(), ((ProtobufFactory) factory).enumtostring))
                            .append(" ")
                            .append(member.getFieldName())
                            .append("\" : ")
                            .append(member.getPosition())
                            .append(i == members.size() - 1 ? "\r\n" : ",\r\n");
                } catch (RuntimeException e) {
                    System.err.println("member = " + member);
                    throw e;
                }
            }
            sb.append(prefix).append(dot ? "}," : "}").append("\r\n");
        } else if ((!(type instanceof Class)
                        || !((Class) type).isArray()
                        || !((Class) type).getComponentType().getName().startsWith("java"))
                && (decoder instanceof ProtobufArrayDecoder || decoder instanceof ProtobufCollectionDecoder)) {
            Type mtype = decoder instanceof ProtobufArrayDecoder
                    ? ((ProtobufArrayDecoder) decoder).getComponentType()
                    : ((ProtobufCollectionDecoder) decoder).getComponentType();
            if (!mtype.toString().startsWith("java")
                    && !mtype.toString().startsWith("class java")
                    && mtype.toString().indexOf('.') > 0) {
                defineJsonDecodeDescriptor(parent, list, mtype, sb, prefix, excludeFunc);
            }
        } else if (sb.length() == 0) {
            if (decoder instanceof SimpledCoder
                    || decoder instanceof StringArraySimpledCoder
                    || (decoder instanceof ProtobufArrayDecoder
                            && ((ProtobufArrayDecoder) decoder).getComponentDecoder() instanceof SimpledCoder)
                    || (decoder instanceof ProtobufCollectionDecoder
                            && ((ProtobufCollectionDecoder) decoder).getComponentDecoder() instanceof SimpledCoder)) {
                sb.append(prefix).append("{\r\n");
                sb.append(prefix)
                        .append("    \"")
                        .append(ProtobufFactory.wireTypeString(type, ((ProtobufFactory) factory).enumtostring))
                        .append(" 0\" : 0\r\n");
                sb.append(prefix).append(dot ? "}," : "}").append("\r\n");
            } else if (decoder instanceof MapDecoder) {
                sb.append(prefix).append("{\r\n");
                sb.append(prefix)
                        .append("    \"")
                        .append(ProtobufFactory.wireTypeString(type, ((ProtobufFactory) factory).enumtostring))
                        .append(" 0\" : 0\r\n");
                sb.append(prefix).append(dot ? "}," : "}").append("\r\n");
            } else {
                throw new ConvertException("Not support type (" + type + ")");
            }
        } else {
            throw new ConvertException("Not support the type (" + type + ")");
        }
    }

    /**
     * 输出结果的类型
     *
     * @param type 输出结果的类型
     * @return String
     */
    public String getJsonEncodeDescriptor(Type type) {
        StringBuilder sb = new StringBuilder();
        defineJsonEncodeDescriptor(null, new ArrayList<>(), type, sb, "", null);
        return sb.toString();
    }

    public String getJsonEncodeDescriptor(Type type, BiFunction<Type, EnMember, Boolean> func) {
        StringBuilder sb = new StringBuilder();
        defineJsonEncodeDescriptor(null, new ArrayList<>(), type, sb, "", func);
        return sb.toString();
    }

    protected String getJsonEncodeDescriptor(
            Type parent, List<String> list, Type type, BiFunction<Type, EnMember, Boolean> func) {
        StringBuilder sb = new StringBuilder();
        defineJsonEncodeDescriptor(parent, list, type, sb, "", func);
        return sb.toString();
    }

    protected void defineJsonEncodeDescriptor(
            Type parent,
            List<String> list,
            Type type,
            StringBuilder sb,
            String prefix,
            BiFunction<Type, EnMember, Boolean> excludeFunc) {
        Encodeable encoder = factory.loadEncoder(type);
        boolean dot = sb.length() > 0;
        if (encoder instanceof ObjectEncoder) {
            if (sb.length() > 0) {
                if (list.contains(parent + "" + defineTypeName(type))) {
                    return;
                }
                list.add(parent + "" + defineTypeName(type));
                sb.append(prefix)
                        .append("\"message ")
                        .append(defineTypeName(type))
                        .append("\" : {\r\n");
            } else {
                sb.append("{\r\n");
            }
            EnMember[] ems = ((ObjectEncoder) encoder).getMembers();
            List<EnMember> members = new ArrayList<>();
            for (EnMember member : ems) {
                if (excludeFunc != null && excludeFunc.apply(type, member)) {
                    continue;
                }
                members.add(member);
            }
            for (EnMember member : members) {
                Type mtype = member.getEncoder().getType();
                if (!(mtype instanceof Class)) {
                    if (mtype instanceof ParameterizedType) {
                        final ParameterizedType pt = (ParameterizedType) mtype;
                        if (pt.getActualTypeArguments().length == 1
                                && (pt.getActualTypeArguments()[0] instanceof Class)) {
                            defineJsonEncodeDescriptor(parent, list, mtype, sb, prefix + "    ", excludeFunc);
                        }
                    } else if (mtype instanceof GenericArrayType) {
                        final GenericArrayType gt = (GenericArrayType) mtype;
                        if (!gt.getGenericComponentType().toString().startsWith("java")
                                && !gt.getGenericComponentType().toString().startsWith("class java")
                                && gt.getGenericComponentType().toString().indexOf('.') > 0) {
                            defineJsonEncodeDescriptor(
                                    parent, list, gt.getGenericComponentType(), sb, prefix + "    ", excludeFunc);
                        }
                    }
                    continue;
                }
                Class mclz = (Class) member.getEncoder().getType();
                if (!mclz.isArray() && !mclz.isEnum() && !mclz.getName().startsWith("java")) {
                    defineJsonEncodeDescriptor(parent, list, mclz, sb, prefix + "    ", excludeFunc);
                } else if (mclz.isArray()
                        && !mclz.getComponentType().getName().startsWith("java")
                        && !mclz.getComponentType().getName().equals("boolean")
                        && !mclz.getComponentType().getName().equals("byte")
                        && !mclz.getComponentType().getName().equals("char")
                        && !mclz.getComponentType().getName().equals("short")
                        && !mclz.getComponentType().getName().equals("int")
                        && !mclz.getComponentType().getName().equals("long")
                        && !mclz.getComponentType().getName().equals("float")
                        && !mclz.getComponentType().getName().equals("double")) {
                    defineJsonEncodeDescriptor(parent, list, mclz.getComponentType(), sb, prefix + "    ", excludeFunc);
                }
            }
            for (int i = 0; i < members.size(); i++) {
                EnMember member = members.get(i);
                try {
                    sb.append(prefix)
                            .append("    \"")
                            .append(ProtobufFactory.wireTypeString(
                                    member.getEncoder().getType(), ((ProtobufFactory) factory).enumtostring))
                            .append(" ")
                            .append(member.getFieldName())
                            .append("\" : ")
                            .append(member.getPosition())
                            .append(i == members.size() - 1 ? "\r\n" : ",\r\n");
                } catch (RuntimeException e) {
                    System.err.println("member = " + member);
                    throw e;
                }
            }
            sb.append(prefix).append(dot ? "}," : "}").append("\r\n");
        } else if (encoder instanceof ProtobufArrayEncoder || encoder instanceof ProtobufCollectionEncoder) {
            Type mtype = encoder instanceof ProtobufArrayEncoder
                    ? ((ProtobufArrayEncoder) encoder).getComponentType()
                    : ((ProtobufCollectionEncoder) encoder).getComponentType();
            if (!mtype.toString().startsWith("java")
                    && !mtype.toString().startsWith("class java")
                    && mtype.toString().indexOf('.') > 0) {
                defineJsonEncodeDescriptor(parent, list, mtype, sb, prefix, excludeFunc);
            }
        } else if (sb.length() == 0) {
            if (encoder instanceof SimpledCoder
                    || (encoder instanceof ProtobufArrayEncoder
                            && ((ProtobufArrayEncoder) encoder).getComponentEncoder() instanceof SimpledCoder)
                    || (encoder instanceof ProtobufCollectionEncoder
                            && ((ProtobufCollectionEncoder) encoder).getComponentEncoder() instanceof SimpledCoder)) {
                sb.append(prefix).append("{\r\n");
                sb.append(prefix)
                        .append("    \"")
                        .append(ProtobufFactory.wireTypeString(type, ((ProtobufFactory) factory).enumtostring))
                        .append(" 0\" : 0\r\n");
                sb.append(prefix).append(dot ? "}," : "}").append("\r\n");
            } else if (encoder instanceof MapEncoder) {
                sb.append(prefix).append("{\r\n");
                sb.append(prefix)
                        .append("    \"")
                        .append(ProtobufFactory.wireTypeString(type, ((ProtobufFactory) factory).enumtostring))
                        .append(" 0\" : 0\r\n");
                sb.append(prefix).append(dot ? "}," : "}").append("\r\n");
            } else {
                throw new ConvertException("Not support the type (" + type + ")");
            }
        } else {
            throw new ConvertException("Not support the type (" + type + ")");
        }
    }

    public String getProtoDescriptor(Type type) {
        StringBuilder sb = new StringBuilder();
        Class clazz = TypeToken.typeToClass(type);
        sb.append("//java ")
                .append(clazz.isArray() ? (clazz.getComponentType().getName() + "[]") : clazz.getName())
                .append("\r\n\r\n");
        if (type instanceof Class) {
            sb.append("option java_package = \"")
                    .append(clazz.getPackage().getName())
                    .append("\";\r\n\r\n");
        }
        sb.append("syntax = \"proto3\";\r\n\r\n");
        // defineProtoDescriptor(type, sb, "");
        defineProtoDescriptor(null, new ArrayList<>(), type, sb, "", null);
        return sb.toString();
    }

    protected String defineProtoDescriptor(
            Type parent, List<String> list, Type type, BiFunction<Type, EnMember, Boolean> func) {
        StringBuilder sb = new StringBuilder();
        defineProtoDescriptor(parent, list, type, sb, "", func);
        return sb.toString();
    }

    protected void defineProtoDescriptor(
            Type parent,
            List<String> list,
            Type type,
            StringBuilder sb,
            String prefix,
            BiFunction<Type, EnMember, Boolean> excludeFunc) {
        Encodeable encoder = factory.loadEncoder(type);
        if (encoder instanceof ObjectEncoder) {
            if (list.contains(parent + "" + defineTypeName(type))) {
                return;
            }
            list.add(parent + "" + defineTypeName(type));

            List<EnMember> members = new ArrayList<>();
            EnMember[] ems = ((ObjectEncoder) encoder).getMembers();
            for (EnMember member : ems) {
                if (excludeFunc != null && excludeFunc.apply(type, member)) {
                    continue;
                }
                members.add(member);
            }
            final List<StringBuilder> sblist = new ArrayList<>();
            for (EnMember member : members) {
                Type mtype = member.getEncoder().getType();
                if (!(mtype instanceof Class)) {
                    if (mtype instanceof ParameterizedType) {
                        final ParameterizedType pt = (ParameterizedType) mtype;
                        if (pt.getActualTypeArguments().length == 1
                                && (pt.getActualTypeArguments()[0] instanceof Class)) {
                            StringBuilder innersb = new StringBuilder();
                            defineProtoDescriptor(parent, list, mtype, innersb, prefix, excludeFunc);
                            sblist.add(innersb);
                        }
                    } else if (mtype instanceof GenericArrayType) {
                        final GenericArrayType gt = (GenericArrayType) mtype;
                        if (!gt.getGenericComponentType().toString().startsWith("java")
                                && !gt.getGenericComponentType().toString().startsWith("class java")
                                && gt.getGenericComponentType().toString().indexOf('.') > 0) {
                            StringBuilder innersb = new StringBuilder();
                            defineProtoDescriptor(
                                    parent, list, gt.getGenericComponentType(), innersb, prefix, excludeFunc);
                            sblist.add(innersb);
                        }
                    }
                    continue;
                }
                Class mclz = (Class) member.getEncoder().getType();
                if (!mclz.isArray() && !mclz.isEnum() && !mclz.getName().startsWith("java")) {
                    StringBuilder innersb = new StringBuilder();
                    defineProtoDescriptor(parent, list, mclz, innersb, prefix, excludeFunc);
                    sblist.add(innersb);
                } else if (mclz.isArray()
                        && !mclz.getComponentType().getName().startsWith("java")
                        && !mclz.getComponentType().getName().equals("boolean")
                        && !mclz.getComponentType().getName().equals("byte")
                        && !mclz.getComponentType().getName().equals("char")
                        && !mclz.getComponentType().getName().equals("short")
                        && !mclz.getComponentType().getName().equals("int")
                        && !mclz.getComponentType().getName().equals("long")
                        && !mclz.getComponentType().getName().equals("float")
                        && !mclz.getComponentType().getName().equals("double")) {
                    StringBuilder innersb = new StringBuilder();
                    defineProtoDescriptor(parent, list, mclz.getComponentType(), innersb, prefix, excludeFunc);
                    sblist.add(innersb);
                }
            }
            for (StringBuilder sbitem : sblist) {
                if (sbitem.length() < 1) {
                    continue;
                }
                sb.append(sbitem.toString().trim()).append("\r\n\r\n");
            }
            sb.append(prefix).append("message ").append(defineTypeName(type)).append(" {\r\n");
            for (int i = 0; i < members.size(); i++) {
                EnMember member = members.get(i);
                try {
                    sb.append(prefix)
                            .append("    ")
                            .append(ProtobufFactory.wireTypeString(
                                    member.getEncoder().getType(), ((ProtobufFactory) factory).enumtostring))
                            .append(" ")
                            .append(member.getFieldName())
                            .append(" = ")
                            .append(member.getPosition())
                            .append(member.getComment().isEmpty() ? ";\r\n" : ("; //" + member.getComment() + " \r\n"));
                } catch (RuntimeException e) {
                    System.err.println("member = " + member);
                    throw e;
                }
            }
            sb.append(prefix).append("}").append("\r\n");
        } else if (encoder instanceof ProtobufArrayEncoder || encoder instanceof ProtobufCollectionEncoder) {
            Type mtype = encoder instanceof ProtobufArrayEncoder
                    ? ((ProtobufArrayEncoder) encoder).getComponentType()
                    : ((ProtobufCollectionEncoder) encoder).getComponentType();
            if (!mtype.toString().startsWith("java")
                    && !mtype.toString().startsWith("class java")
                    && mtype.toString().indexOf('.') > 0) {
                defineProtoDescriptor(parent, list, mtype, sb, prefix, excludeFunc);
            }
        } else {
            throw new ConvertException("Not support the type (" + type + ")");
        }
    }

    protected StringBuilder defineTypeName(Type type) {
        StringBuilder sb = new StringBuilder();
        if (type instanceof Class) {
            sb.append(((Class) type).getSimpleName().replace("[]", "_Array"));
        } else if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            sb.append(((Class) raw).getSimpleName().replace("[]", "_Array"));
            Type[] ts = ((ParameterizedType) type).getActualTypeArguments();
            if (ts != null) {
                for (Type t : ts) {
                    if (t != null) {
                        sb.append('_').append(defineTypeName(t));
                    }
                }
            }
        }
        return sb;
    }

    // ------------------------------ convertFrom -----------------------------------------------------------
    @Override
    public <T> T convertFrom(final Type type, final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return convertFrom(type, bytes, 0, bytes.length);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final byte[] bytes, final int offset, final int len) {
        if (type == null) {
            return null;
        }
        final ProtobufReader reader = new ProtobufReader(bytes, offset, len);
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        if (!(decoder instanceof ObjectDecoder) && !(decoder instanceof SimpledCoder)) {
            throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + type + ")");
        }
        T rs = (T) decoder.convertFrom(reader);
        return rs;
    }

    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final InputStream in) {
        if (type == null || in == null) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        if (!(decoder instanceof ObjectDecoder)) {
            throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + type + ")");
        }
        return (T) decoder.convertFrom(new ProtobufStreamReader(in));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final ByteBuffer... buffers) {
        if (type == null || Utility.isEmpty(buffers)) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        if (!(decoder instanceof ObjectDecoder)) {
            throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + type + ")");
        }
        return (T) decoder.convertFrom(new ProtobufByteBufferReader(buffers));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final ProtobufReader reader) {
        if (type == null) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        if (!(decoder instanceof ObjectDecoder)) {
            throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + type + ")");
        }
        T rs = (T) decoder.convertFrom(reader);
        return rs;
    }

    // ------------------------------ convertTo -----------------------------------------------------------
    @Override
    public byte[] convertTo(final Type type, final Object value) {
        if (value == null) {
            final ProtobufWriter writer = pollWriter();
            writer.writeNull();
            byte[] result = writer.toArray();
            offerWriter(writer);
            return result;
        }
        final Type t = type == null ? value.getClass() : type;
        final ProtobufWriter writer = pollWriter();
        Encodeable encoder = this.lastEncodeable;
        if (encoder == null || encoder.getType() != t) {
            encoder = factory.loadEncoder(t);
            this.lastEncodeable = encoder;
            if (!(encoder instanceof ObjectEncoder) && !(encoder instanceof SimpledCoder)) {
                throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + t + ")");
            }
        }
        if (encoder.specifyable()) {
            writer.specificObjectType(t);
        }
        encoder.convertTo(writer, value);
        byte[] result = writer.toArray();
        offerWriter(writer);
        return result;
    }

    public byte[] convertTo(final Object value, int tag, byte... appends) {
        return convertTo(value.getClass(), value, tag, appends);
    }

    public byte[] convertTo(final Type type, final Object value, int tag, byte... appends) {
        if (type == null) {
            return null;
        }
        final ProtobufWriter writer = pollWriter();
        Encodeable encoder = this.lastEncodeable;
        if (encoder == null || encoder.getType() != type) {
            encoder = factory.loadEncoder(type);
            this.lastEncodeable = encoder;
        }
        if (encoder.specifyable()) {
            writer.specificObjectType(type);
        }
        if (!(encoder instanceof ObjectEncoder) && !(encoder instanceof SimpledCoder)) {
            throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + type + ")");
        }
        encoder.convertTo(writer, value);
        writer.writeUInt32(tag);
        writer.writeUInt32(appends.length);
        writer.writeTo(appends);
        byte[] result = writer.toArray();
        offerWriter(writer);
        return result;
    }

    @Override
    public byte[] convertToBytes(final Type type, final Object value) {
        return convertTo(type, value);
    }

    @Override
    public void convertToBytes(final Type type, final Object value, final ConvertBytesHandler handler) {
        final ProtobufWriter writer = pollWriter();
        if (value == null) {
            writer.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(t);
            }
            if (!(encoder instanceof ObjectEncoder) && !(encoder instanceof SimpledCoder)) {
                throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + t + ")");
            }
            encoder.convertTo(writer, value);
        }
        writer.completed(handler, writerConsumer);
    }

    @Override
    public void convertToBytes(final ByteArray array, final Type type, final Object value) {
        Objects.requireNonNull(array);
        final ProtobufWriter writer = configWrite(new ProtobufWriter(array)
                .withFeatures(features)
                .enumtostring(((ProtobufFactory) factory).enumtostring));
        if (value == null) {
            writer.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(t);
            }
            if (!(encoder instanceof ObjectEncoder) && !(encoder instanceof SimpledCoder)) {
                throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + t + ")");
            }
            encoder.convertTo(writer, value);
        }
        writer.directTo(array);
    }

    public void convertTo(final OutputStream out, final Type type, final Object value) {
        ProtobufWriter writer = pollProtobufWriter(out);
        if (value == null) {
            writer.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(t);
            }
            if (!(encoder instanceof ObjectEncoder)) {
                throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + t + ")");
            }
            encoder.convertTo(writer, value);
        }
    }

    @Override
    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, final Object value) {
        Objects.requireNonNull(supplier);
        ProtobufByteBufferWriter writer = pollProtobufWriter(supplier);
        if (value == null) {
            writer.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(t);
            }
            if (!(encoder instanceof ObjectEncoder)) {
                throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + t + ")");
            }
            encoder.convertTo(writer, value);
        }
        return writer.toBuffers();
    }

    @Override
    public void convertTo(final ProtobufWriter writer, final Type type, final Object value) {
        if (value == null) {
            writer.writeNull();
            return;
        }
        writer.configWrite();
        final Type t = type == null ? value.getClass() : type;
        Encodeable encoder = this.lastEncodeable;
        if (encoder == null || encoder.getType() != t) {
            encoder = factory.loadEncoder(t);
            this.lastEncodeable = encoder;
        }
        if (encoder.specifyable()) {
            writer.specificObjectType(t);
        }
        encoder.convertTo(writer, value);
    }

    public ProtobufWriter convertToWriter(final Type type, final Object value) {
        if (value == null) {
            return null;
        }
        final ProtobufWriter writer = pollWriter();
        final Type t = type == null ? value.getClass() : type;
        Encodeable encoder = this.lastEncodeable;
        if (encoder == null || encoder.getType() != t) {
            encoder = factory.loadEncoder(t);
            this.lastEncodeable = encoder;
        }
        if (encoder.specifyable()) {
            writer.specificObjectType(t);
        }
        if (!(encoder instanceof ObjectEncoder)) {
            throw new ConvertException(this.getClass().getSimpleName() + " not supported type(" + t + ")");
        }
        encoder.convertTo(writer, value);
        return writer;
    }
}
