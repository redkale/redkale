/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.Map;
import org.redkale.convert.*;
import static org.redkale.convert.pb.ProtobufMapEncoder.createAttribute;

/**
 * @author zhangjx
 * @param <K> K
 * @param <V> V
 */
public class ProtobufMapDecoder<K, V> extends MapDecoder<ProtobufReader, K, V>
        implements ProtobufTagDecodeable<ProtobufReader, Map<K, V>> {

    protected final DeMember keyMember;

    protected final DeMember valueMember;

    public ProtobufMapDecoder(ConvertFactory factory, Type type) {
        super(factory, type);
        boolean enumtostring = ((ProtobufFactory) factory).enumtostring;
        int keyTag = ProtobufFactory.getTag("key", keyDecoder.getType(), 1, enumtostring);
        int valTag = ProtobufFactory.getTag("value", valueDecoder.getType(), 2, enumtostring);
        this.keyMember = new DeMember(createAttribute("key", keyDecoder.getType()), keyTag, keyDecoder);
        this.valueMember = new DeMember(createAttribute("value", valueDecoder.getType()), valTag, valueDecoder);
        setTagSize(keyMember, ProtobufFactory.computeSInt32SizeNoTag(keyMember.getTag()));
        setTagSize(valueMember, ProtobufFactory.computeSInt32SizeNoTag(valueMember.getTag()));
    }

    @Override
    public Map<K, V> convertFrom(ProtobufReader in, DeMember member) {
        this.checkInited();
        in.readMapB(this.keyDecoder, this.valueDecoder);
        final Map<K, V> result = this.creator.create();
        Decodeable<ProtobufReader, K> kdecoder = this.keyDecoder;
        Decodeable<ProtobufReader, V> vdecoder = this.valueDecoder;
        final int limit = in.limit();
        while (in.hasNext()) {
            int contentLen = in.readRawVarint32();
            in.limit(in.position() + contentLen + 1);
            in.readTag(); // key tag
            K key = kdecoder.convertFrom(in);
            in.readTag(); // value tag
            V value = vdecoder.convertFrom(in);
            result.put(key, value);
            in.limit(limit);
            if (!in.readNextTag(member)) { // 元素结束
                break;
            }
        }
        in.readMapE();
        return result;
    }
}
