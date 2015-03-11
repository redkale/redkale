/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.util;

/**
 *
 * @author zhangjx
 */
public final class TwoLong extends Number implements Comparable<TwoLong> {

    private long first;

    private long second;

    public TwoLong(long one, long two) {
        this.first = one;
        this.second = two;
    }

    public long getFirst() {
        return first;
    }

    public long getSecond() {
        return second;
    }

    public boolean compare(long one, long two) {
        return this.first == one && this.second == two;
    }

    @Override
    public int hashCode() {
        return intValue();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final TwoLong other = (TwoLong) obj;
        return (this.first == other.first && this.second == other.second);
    }

    @Override
    public String toString() {
        return this.first + "_" + this.second;
    }

    @Override
    public int intValue() {
        return (int) longValue();
    }

    @Override
    public long longValue() {
        return first ^ second;
    }

    @Override
    public float floatValue() {
        return (float) longValue();
    }

    @Override
    public double doubleValue() {
        return (double) longValue();
    }

    @Override
    public int compareTo(TwoLong o) {
        return (int) (first == o.first ? (second - o.second) : (first - o.first));
    }

}
