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
 * @param <S> 分数
 *
 * @since 2.8.0
 */
public interface CacheScoredValue<S extends Number> extends Serializable, Comparable<CacheScoredValue> {

    public S getScore();

    public String getValue();

    public static NumberScoredValue create(Number score, String value) {
        return new NumberScoredValue(score, value);
    }

    public static final class NumberScoredValue implements CacheScoredValue<Number> {

        @ConvertColumn(index = 1)
        private Number score;

        @ConvertColumn(index = 2)
        private String value;

        public NumberScoredValue(Number score, String value) {
            Objects.requireNonNull(score);
            Objects.requireNonNull(value);
            this.score = score;
            this.value = value;
        }

        public NumberScoredValue(CacheScoredValue scoredValue) {
            this.score = scoredValue.getScore();
            this.value = scoredValue.getValue();
        }

        @Override
        public Number getScore() {
            return score;
        }

        @Override
        public String getValue() {
            return value;
        }

        public void setScore(Long score) {
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
            final NumberScoredValue other = (NumberScoredValue) obj;
            return Objects.equals(this.value, other.value);
        }

        @Override
        public String toString() {
            return "{score:" + score + ", value:" + value + "}";
        }

    }

}
