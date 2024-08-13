/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <K> K
 * @param <V> V
 */
public class ProtobufMapDecoder<K, V> extends MapDecoder<K, V> {

    private final boolean enumtostring;

    public ProtobufMapDecoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.enumtostring = ((ProtobufFactory) factory).enumtostring;
    }

    @Override
    protected Reader getEntryReader(Reader in, DeMember member, boolean first) {
        ProtobufReader reader = (ProtobufReader) in;
        if (!first && member != null) {
            int tag = reader.readTag();
            if (tag != member.getTag()) {
                reader.backTag(tag);
                return null;
            }
        }
        byte[] bs = reader.readByteArray();
        return new ProtobufReader(bs);
    }

    @Override
    protected K readKeyMember(Reader in, DeMember member, Decodeable<Reader, K> decoder, boolean first) {
        ProtobufReader reader = (ProtobufReader) in;
        reader.readTag();
        return decoder.convertFrom(in);
    }

    @Override
    protected V readValueMember(Reader in, DeMember member, Decodeable<Reader, V> decoder, boolean first) {
        ProtobufReader reader = (ProtobufReader) in;
        reader.readTag();
        return decoder.convertFrom(in);
    }
}
