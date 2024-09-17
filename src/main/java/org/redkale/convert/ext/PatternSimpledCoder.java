/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.util.regex.Pattern;
import org.redkale.convert.*;
import org.redkale.util.Attribute;

/**
 * Pattern 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class PatternSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Pattern> {

    public static final PatternSimpledCoder instance = new PatternSimpledCoder();

    protected final PatternObjectEncoder encoder = new PatternObjectEncoder();

    protected final PatternObjectDecoder decoder = new PatternObjectDecoder();

    @Override
    public void convertTo(W out, Pattern value) {
        encoder.convertTo(out, value);
    }

    @Override
    public Pattern convertFrom(R in) {
        return decoder.convertFrom(in);
    }

    protected static class PatternObjectEncoder extends ObjectEncoder<Writer, Pattern> {
        protected PatternObjectEncoder() {
            super(Pattern.class);
            EnMember flagsMember = new EnMember(
                    Attribute.create(Pattern.class, "flags", int.class, t -> t.flags(), null),
                    1,
                    IntSimpledCoder.instance);
            EnMember patternMember = new EnMember(
                    Attribute.create(Pattern.class, "pattern", String.class, t -> t.pattern(), null),
                    2,
                    StringSimpledCoder.instance);
            this.initFieldMember(new EnMember[] {flagsMember, patternMember});
            this.inited = true;
        }
    }

    protected static class PatternObjectDecoder extends ObjectDecoder<Reader, Pattern> {
        protected PatternObjectDecoder() {
            super(Pattern.class);
            DeMember flagsMember = new DeMember(
                    Attribute.create(Pattern.class, "flags", int.class, t -> t.flags(), null),
                    1,
                    IntSimpledCoder.instance);
            DeMember patternMember = new DeMember(
                    Attribute.create(Pattern.class, "pattern", String.class, t -> t.pattern(), null),
                    2,
                    StringSimpledCoder.instance);
            this.creator = args -> Pattern.compile((String) args[1], (Integer) args[0]);
            this.initFieldMember(new DeMember[] {flagsMember, patternMember});
            this.creatorConstructorMembers = this.getMembers();
            this.inited = true;
        }
    }
}
