/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.util.function.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.util.Times;

/**
 * 取值范围，包含两边的值
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <E> Comparable的子类型
 */
public interface Range<E extends Comparable> extends java.io.Serializable, Predicate<E> {

    public E getMin();

    public E getMax();

    public static ByteRange create(byte min, byte max) {
        return new ByteRange(min, max);
    }

    public static ShortRange create(short min, short max) {
        return new ShortRange(min, max);
    }

    public static IntRange create(int min, int max) {
        return new IntRange(min, max);
    }

    public static LongRange create(long min, long max) {
        return new LongRange(min, max);
    }

    public static FloatRange create(float min, float max) {
        return new FloatRange(min, max);
    }

    public static DoubleRange create(double min, double max) {
        return new DoubleRange(min, max);
    }

    public static StringRange create(String min, String max) {
        return new StringRange(min, max);
    }

    public static final class ByteRange implements Range<Byte> {

        @ConvertColumn(index = 1)
        private Byte min = Byte.MIN_VALUE;

        @ConvertColumn(index = 2)
        private Byte max = Byte.MAX_VALUE;

        public ByteRange() {}

        public ByteRange(Byte min, Byte max) {
            if (min != null) this.min = min;
            if (max != null) this.max = max;
        }

        @Override
        public Byte getMin() {
            return min;
        }

        @Override
        public Byte getMax() {
            return max;
        }

        public void setMin(Byte min) {
            if (min != null) this.min = min;
        }

        public void setMax(Byte max) {
            if (max != null) this.max = max;
        }

        @Override
        public boolean test(Byte t) {
            if (max < min && max <= 0) return t >= min;
            return t >= min && t <= max;
        }

        @Override
        public String toString() {
            return "{min:" + min + ", max:" + max + "}";
        }
    }

    public static final class ShortRange implements Range<Short> {

        @ConvertColumn(index = 1)
        private Short min = Short.MIN_VALUE;

        @ConvertColumn(index = 2)
        private Short max = Short.MAX_VALUE;

        public ShortRange() {}

        public ShortRange(Short min, Short max) {
            if (min != null) this.min = min;
            if (max != null) this.max = max;
        }

        @Override
        public Short getMin() {
            return min;
        }

        @Override
        public Short getMax() {
            return max;
        }

        public void setMin(Short min) {
            if (min != null) this.min = min;
        }

        public void setMax(Short max) {
            if (max != null) this.max = max;
        }

        @Override
        public boolean test(Short t) {
            if (max < min && max <= 0) return t >= min;
            return t >= min && t <= max;
        }

        @Override
        public String toString() {
            return "{min:" + min + ", max:" + max + "}";
        }
    }

    public static final class IntRange implements Range<Integer> {

        @ConvertColumn(index = 1)
        private Integer min = Integer.MIN_VALUE;

        @ConvertColumn(index = 2)
        private Integer max = Integer.MAX_VALUE;

        public IntRange() {}

        public IntRange(Integer min, Integer max) {
            if (min != null) this.min = min;
            if (max != null) this.max = max;
        }

        @Override
        public Integer getMin() {
            return min;
        }

        @Override
        public Integer getMax() {
            return max;
        }

        public void setMin(Integer min) {
            if (min != null) this.min = min;
        }

        public void setMax(Integer max) {
            if (max != null) this.max = max;
        }

        @Override
        public boolean test(Integer t) {
            if (max < min && max <= 0) return t >= min;
            return t >= min && t <= max;
        }

        @Override
        public String toString() {
            return "{min:" + min + ", max:" + max + "}";
        }
    }

    public static final class LongRange implements Range<Long> {

        @ConvertColumn(index = 1)
        private Long min = Long.MIN_VALUE;

        @ConvertColumn(index = 2)
        private Long max = Long.MAX_VALUE;

        public LongRange() {}

        public LongRange(Long min, Long max) {
            if (min != null) this.min = min;
            if (max != null) this.max = max;
        }

        public static LongRange todayRange() {
            long min = Times.midnight();
            return new LongRange(min, min + 24 * 60 * 60 * 1000 - 1);
        }

        public static LongRange yesterdayRange() {
            long min = Times.midnight(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
            return new LongRange(min, min + 24 * 60 * 60 * 1000 - 1);
        }

        @Override
        public Long getMin() {
            return min;
        }

        @Override
        public Long getMax() {
            return max;
        }

        public void setMin(Long min) {
            if (min != null) this.min = min;
        }

        public void setMax(Long max) {
            if (max != null) this.max = max;
        }

        @Override
        public boolean test(Long t) {
            if (max < min && max <= 0) return t >= min;
            return t >= min && t <= max;
        }

        @Override
        public String toString() {
            return "{min:" + min + ", max:" + max + "}";
        }
    }

    public static final class FloatRange implements Range<Float> {

        @ConvertColumn(index = 1)
        private Float min = Float.MIN_VALUE;

        @ConvertColumn(index = 2)
        private Float max = Float.MAX_VALUE;

        public FloatRange() {}

        public FloatRange(Float min, Float max) {
            if (min != null) this.min = min;
            if (max != null) this.max = max;
        }

        @Override
        public Float getMin() {
            return min;
        }

        @Override
        public Float getMax() {
            return max;
        }

        public void setMin(Float min) {
            if (min != null) this.min = min;
        }

        public void setMax(Float max) {
            if (max != null) this.max = max;
        }

        @Override
        public boolean test(Float t) {
            if (max < min && max <= 0) return t >= min;
            return t >= min && t <= max;
        }

        @Override
        public String toString() {
            return "{min:" + min + ", max:" + max + "}";
        }
    }

    public static final class DoubleRange implements Range<Double> {

        @ConvertColumn(index = 1)
        private Double min = Double.MIN_VALUE;

        @ConvertColumn(index = 2)
        private Double max = Double.MAX_VALUE;

        public DoubleRange() {}

        public DoubleRange(Double min, Double max) {
            if (min != null) this.min = min;
            if (max != null) this.max = max;
        }

        @Override
        public Double getMin() {
            return min;
        }

        @Override
        public Double getMax() {
            return max;
        }

        public void setMin(Double min) {
            if (min != null) this.min = min;
        }

        public void setMax(Double max) {
            if (max != null) this.max = max;
        }

        @Override
        public boolean test(Double t) {
            if (max < min && max <= 0) return t >= min;
            return t >= min && t <= max;
        }

        @Override
        public String toString() {
            return "{min:" + min + ", max:" + max + "}";
        }
    }

    public static final class StringRange implements Range<String> {

        @ConvertColumn(index = 1)
        private String min = "";

        @ConvertColumn(index = 2)
        private String max = "";

        public StringRange() {}

        public StringRange(String min, String max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String getMin() {
            return min;
        }

        @Override
        public String getMax() {
            return max;
        }

        public void setMin(String min) {
            if (min != null) this.min = min;
        }

        public void setMax(String max) {
            if (max != null) this.max = max;
        }

        @Override
        public boolean test(String t) {
            return t.compareTo(min) >= 0 && t.compareTo(max) <= 0;
        }

        @Override
        public String toString() {
            return "{min:'" + min + "', max:'" + max + "'}";
        }
    }
}
