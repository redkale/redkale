/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.Map;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <K> K
 * @param <V> V
 */
public class ProtobufMapDecoder<K, V> extends MapDecoder<ProtobufReader, K, V> {

    protected final boolean enumtostring;

    public ProtobufMapDecoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.enumtostring = ((ProtobufFactory) factory).enumtostring;
    }

    @Override
    public Map<K, V> convertFrom(ProtobufReader in, DeMember member) {
        this.checkInited();
        in.readMapB(member, this.keyDecoder, this.valueDecoder);
        final Map<K, V> result = this.creator.create();
        boolean first = true;
        Decodeable<ProtobufReader, K> kdecoder = this.keyDecoder;
        Decodeable<ProtobufReader, V> vdecoder = this.valueDecoder;
        while (in.hasNext()) {
            ProtobufReader entryReader = getEntryReader(in, member, first);
            if (entryReader == null) {
                break;
            }
            entryReader.readTag();
            K key = kdecoder.convertFrom(entryReader);
            entryReader.readTag();
            V value = vdecoder.convertFrom(entryReader);
            result.put(key, value);
            first = false;
        }
        in.readMapE();
        return result;
    }

    protected ProtobufReader getEntryReader(ProtobufReader in, DeMember member, boolean first) {
        if (!first && member != null) {
            int tag = in.readTag();
            if (tag != member.getTag()) {
                in.backTag(tag);
                return null;
            }
        }
        byte[] bs = in.readByteArray();
        return new ProtobufReader(bs);
    }
}
