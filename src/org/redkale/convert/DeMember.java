/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.util.Attribute;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <T>
 * @param <F>
 */
@SuppressWarnings("unchecked")
public final class DeMember<R extends Reader, T, F> implements Comparable<DeMember<R, T, F>> {

    protected final Attribute<T, F> attribute;

    protected Decodeable<R, F> decoder;

    public DeMember(final Attribute<T, F> attribute) {
        this.attribute = attribute;
    }

    public DeMember(Attribute<T, F> attribute, Decodeable<R, F> decoder) {
        this(attribute);
        this.decoder = decoder;
    }

    public final void read(R in, T obj) {
        this.attribute.set(obj, decoder.convertFrom(in));
    }

    public Attribute<T, F> getAttribute() {
        return this.attribute;
    }

    @Override
    public final int compareTo(DeMember<R, T, F> o) {
        if (o == null) return 1;
        return this.attribute.field().compareTo(o.attribute.field());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DeMember)) return false;
        DeMember other = (DeMember) obj;
        return compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return this.attribute.field().hashCode();
    }

    @Override
    public String toString() {
        return "DeMember{" + "attribute=" + attribute.field() + ", decoder=" + decoder + '}';
    }
}
