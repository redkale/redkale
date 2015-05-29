/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert;

import com.wentch.redkale.util.Attribute;

/**
 *
 * @author zhangjx
 * @param <W>
 * @param <T>
 * @param <F>
 */
@SuppressWarnings("unchecked")
public final class EnMember<W extends Writer, T, F> implements Comparable<EnMember<W, T, F>> {

    private final Attribute<T, F> attribute;

    final Encodeable<W, F> encoder;

    private final boolean istring;

    private final boolean isnumber;

    private final boolean isbool;

    public EnMember(Attribute<T, F> attribute, Encodeable<W, F> encoder) {
        this.attribute = attribute;
        this.encoder = encoder;
        this.istring = CharSequence.class.isAssignableFrom(attribute.type());
        this.isnumber = Number.class.isAssignableFrom(attribute.type()) || attribute.type().isPrimitive();
        this.isbool = attribute.type() == Boolean.class || attribute.type() == boolean.class;
    }

    public boolean write(final W out, final boolean comma, final T obj) {
        F value = attribute.get(obj);
        if (value == null) return comma;
        if (out.isTiny()) {
            if (isnumber) {
                if (((Number) value).intValue() == 0) return comma;
            } else if (istring) {
                if (((CharSequence) value).length() == 0) return comma;
            } else if (isbool) {
                if (!((Boolean) value)) return comma;
            }
        }
        out.writeField(comma, attribute);
        encoder.convertTo(out, value);
        return true;
    }

    @Override
    public final int compareTo(EnMember<W, T, F> o) {
        if (o == null) return 1;
        return this.attribute.field().compareTo(o.attribute.field());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EnMember)) return false;
        EnMember other = (EnMember) obj;
        return compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return this.attribute.field().hashCode();
    }

    @Override
    public String toString() {
        return "EnMember{" + "attribute=" + attribute.field() + ", encoder=" + encoder + '}';
    }
}
