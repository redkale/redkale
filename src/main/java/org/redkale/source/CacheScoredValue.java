/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.Objects;
import org.redkale.convert.ConvertColumn;

/**
 *
 * 有序集合的对象类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class CacheScoredValue implements Serializable, Comparable<CacheScoredValue> {

    @ConvertColumn(index = 1)
    private Number score;

    @ConvertColumn(index = 2)
    private String value;

    public CacheScoredValue() {
    }

    protected CacheScoredValue(Number score, String value) {
        Objects.requireNonNull(score);
        Objects.requireNonNull(value);
        this.score = score;
        this.value = value;
    }

    protected CacheScoredValue(CacheScoredValue scoredValue) {
        this.score = scoredValue.getScore();
        this.value = scoredValue.getValue();
    }

    public static CacheScoredValue create(Number score, String value) {
        return new CacheScoredValue(score, value);
    }

    public Number getScore() {
        return score;
    }

    public String getValue() {
        return value;
    }

    public void setScore(Number score) {
        this.score = score;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public int compareTo(CacheScoredValue o) {
        return ((Comparable) this.score).compareTo((Number) o.getScore());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CacheScoredValue other = (CacheScoredValue) obj;
        return Objects.equals(this.value, other.value);
    }

    @Override
    public String toString() {
        return "{score:" + score + ", value:" + value + "}";
    }

}
