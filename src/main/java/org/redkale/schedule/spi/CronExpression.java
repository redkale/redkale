/*
 *
 */
package org.redkale.schedule.spi;

import java.time.DateTimeException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.ValueRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.redkale.annotation.Nullable;
import org.redkale.util.RedkaleException;
import org.redkale.util.Utility;

/**
 * cron定时表达式解析器 <br>
 * 代码复制于org.springframework.scheduling.support.CronExpression
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class CronExpression {

    static final int MAX_ATTEMPTS = 366;

    private static final String[] MACROS = new String[] {
        "@yearly", "0 0 0 1 1 *",
        "@annually", "0 0 0 1 1 *",
        "@monthly", "0 0 0 1 * *",
        "@monthly10s", "10 0 0 1 * *",
        "@monthly30s", "30 0 0 1 * *",
        "@monthly1m", "0 1 0 1 * *",
        "@monthly5m", "0 5 0 1 * *",
        "@monthly15m", "0 15 0 1 * *",
        "@monthly30m", "0 30 0 1 * *",
        "@monthly1h", "0 0 1 1 * *",
        "@monthly2h", "0 0 2 1 * *",
        "@weekly", "0 0 0 * * 0",
        "@daily", "0 0 0 * * *",
        "@daily10s", "10 0 0 * * *",
        "@daily30s", "30 0 0 * * *",
        "@daily1m", "0 1 0 * * *",
        "@daily5m", "0 5 0 * * *",
        "@daily15m", "0 15 0 * * *",
        "@daily30m", "0 30 0 * * *",
        "@daily1h", "0 0 1 * * *",
        "@daily2h", "0 0 2 * * *",
        "@midnight", "0 0 0 * * *",
        "@midnight10s", "10 0 0 * * *",
        "@midnight30s", "30 0 0 * * *",
        "@midnight1m", "0 1 0 * * *",
        "@midnight5m", "0 5 0 * * *",
        "@midnight15m", "0 15 0 * * *",
        "@midnight30m", "0 30 0 * * *",
        "@midnight1h", "0 0 1 * * *",
        "@midnight2h", "0 0 2 * * *",
        "@hourly", "0 0 * * * *",
        "@minutely", "0 0/1 * * * *",
        "@1m", "0 0/1 * * * *",
        "@2m", "0 0/2 * * * *",
        "@3m", "0 0/3 * * * *",
        "@5m", "0 0/5 * * * *",
        "@10m", "0 0/10 * * * *",
        "@15m", "0 0/15 * * * *",
        "@30m", "0 0/30 * * * *",
        "@1h", "0 0 * * * *",
        "@2h", "0 0 0/2 * * *",
        "@3h", "0 0 0/3 * * *",
        "@6h", "0 0 0/6 * * *"
    };

    private final CronField[] fields;

    private final String expression;

    private CronExpression(
            CronField seconds,
            CronField minutes,
            CronField hours,
            CronField daysOfMonth,
            CronField months,
            CronField daysOfWeek,
            String expression) {
        this.fields = new CronField[] {daysOfWeek, months, daysOfMonth, hours, minutes, seconds, CronField.zeroNanos()};
        this.expression = expression;
    }

    public static CronExpression parse(String expression) {
        if (Utility.isBlank(expression)) {
            throw new RedkaleException("Expression string must not be empty");
        }
        expression = resolveMacros(expression);
        String[] fields = expression.split("\\s+");
        if (fields.length != 6) {
            throw new RedkaleException(String.format(
                    "Cron expression must consist of 6 fields (found %d in \"%s\")", fields.length, expression));
        }
        try {
            CronField seconds = CronField.parseSeconds(fields[0]);
            CronField minutes = CronField.parseMinutes(fields[1]);
            CronField hours = CronField.parseHours(fields[2]);
            CronField daysOfMonth = CronField.parseDaysOfMonth(fields[3]);
            CronField months = CronField.parseMonth(fields[4]);
            CronField daysOfWeek = CronField.parseDaysOfWeek(fields[5]);
            return new CronExpression(seconds, minutes, hours, daysOfMonth, months, daysOfWeek, expression);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() + " in cron expression \"" + expression + "\"";
            throw new RedkaleException(msg, ex);
        }
    }

    private static String resolveMacros(String expression) {
        expression = expression.trim();
        for (int i = 0; i < MACROS.length; i = i + 2) {
            if (MACROS[i].equalsIgnoreCase(expression)) {
                return MACROS[i + 1];
            }
        }
        return expression;
    }

    @Nullable
    public <T extends Temporal & Comparable<? super T>> T next(T temporal) {
        return nextOrSame(ChronoUnit.NANOS.addTo(temporal, 1));
    }

    @Nullable
    private <T extends Temporal & Comparable<? super T>> T nextOrSame(T temporal) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            T result = nextOrSameInternal(temporal);
            if (result == null || result.equals(temporal)) {
                return result;
            }
            temporal = result;
        }
        return null;
    }

    @Nullable
    private <T extends Temporal & Comparable<? super T>> T nextOrSameInternal(T temporal) {
        for (CronField field : this.fields) {
            temporal = field.nextOrSame(temporal);
            if (temporal == null) {
                return null;
            }
        }
        return temporal;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CronExpression)) {
            return false;
        }
        CronExpression that = (CronExpression) other;
        return Arrays.equals(this.fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.fields);
    }

    @Override
    public String toString() {
        return this.expression;
    }

    abstract static class CronField {

        private static final String[] MONTHS =
                new String[] {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};

        private static final String[] DAYS = new String[] {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

        private final Type type;

        protected CronField(Type type) {
            this.type = type;
        }

        public static CronField zeroNanos() {
            return BitsCronField.zeroNanos();
        }

        public static CronField parseSeconds(String value) {
            return BitsCronField.parseSeconds(value);
        }

        public static CronField parseMinutes(String value) {
            return BitsCronField.parseMinutes(value);
        }

        public static CronField parseHours(String value) {
            return BitsCronField.parseHours(value);
        }

        public static CronField parseDaysOfMonth(String value) {
            return BitsCronField.parseDaysOfMonth(value);
        }

        public static CronField parseMonth(String value) {
            value = replaceOrdinals(value, MONTHS);
            return BitsCronField.parseMonth(value);
        }

        public static CronField parseDaysOfWeek(String value) {
            value = replaceOrdinals(value, DAYS);
            return BitsCronField.parseDaysOfWeek(value);
        }

        private static String replaceOrdinals(String value, String[] list) {
            value = value.toUpperCase();
            for (int i = 0; i < list.length; i++) {
                String replacement = Integer.toString(i + 1);
                value = replace(value, list[i], replacement);
            }
            return value;
        }

        private static String replace(String inString, String oldPattern, @Nullable String newPattern) {
            if (Utility.isEmpty(inString) || Utility.isEmpty(oldPattern) || newPattern == null) {
                return inString;
            }
            int index = inString.indexOf(oldPattern);
            if (index == -1) {
                // no occurrence -> can return input as-is
                return inString;
            }

            int capacity = inString.length();
            if (newPattern.length() > oldPattern.length()) {
                capacity += 16;
            }
            StringBuilder sb = new StringBuilder(capacity);

            int pos = 0; // our position in the old string
            int patLen = oldPattern.length();
            while (index >= 0) {
                sb.append(inString, pos, index);
                sb.append(newPattern);
                pos = index + patLen;
                index = inString.indexOf(oldPattern, pos);
            }

            // append any characters to the right of a match
            sb.append(inString, pos, inString.length());
            return sb.toString();
        }

        private static String[] delimitedListToStringArray(@Nullable String str, @Nullable String delimiter) {
            return delimitedListToStringArray(str, delimiter, null);
        }

        private static String[] delimitedListToStringArray(String str, String delimiter, String charsToDelete) {

            if (str == null) {
                return new String[0];
            }
            if (delimiter == null) {
                return new String[] {str};
            }

            List<String> result = new ArrayList<>();
            if (delimiter.isEmpty()) {
                for (int i = 0; i < str.length(); i++) {
                    result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
                }
            } else {
                int pos = 0;
                int delPos;
                while ((delPos = str.indexOf(delimiter, pos)) != -1) {
                    result.add(deleteAny(str.substring(pos, delPos), charsToDelete));
                    pos = delPos + delimiter.length();
                }
                if (str.length() > 0 && pos <= str.length()) {
                    // Add rest of String, but not in case of empty input.
                    result.add(deleteAny(str.substring(pos), charsToDelete));
                }
            }
            return result.toArray(new String[result.size()]);
        }

        private static String deleteAny(String inString, @Nullable String charsToDelete) {
            if (Utility.isEmpty(inString) || Utility.isEmpty(charsToDelete)) {
                return inString;
            }
            int lastCharIndex = 0;
            char[] result = new char[inString.length()];
            for (int i = 0; i < inString.length(); i++) {
                char c = inString.charAt(i);
                if (charsToDelete.indexOf(c) == -1) {
                    result[lastCharIndex++] = c;
                }
            }
            if (lastCharIndex == inString.length()) {
                return inString;
            }
            return new String(result, 0, lastCharIndex);
        }

        @Nullable
        public abstract <T extends Temporal & Comparable<? super T>> T nextOrSame(T temporal);

        protected Type type() {
            return this.type;
        }

        @SuppressWarnings("unchecked")
        protected static <T extends Temporal & Comparable<? super T>> T cast(Temporal temporal) {
            return (T) temporal;
        }

        protected enum Type {
            NANO(ChronoField.NANO_OF_SECOND, ChronoUnit.SECONDS),
            SECOND(ChronoField.SECOND_OF_MINUTE, ChronoUnit.MINUTES, ChronoField.NANO_OF_SECOND),
            MINUTE(
                    ChronoField.MINUTE_OF_HOUR,
                    ChronoUnit.HOURS,
                    ChronoField.SECOND_OF_MINUTE,
                    ChronoField.NANO_OF_SECOND),
            HOUR(
                    ChronoField.HOUR_OF_DAY,
                    ChronoUnit.DAYS,
                    ChronoField.MINUTE_OF_HOUR,
                    ChronoField.SECOND_OF_MINUTE,
                    ChronoField.NANO_OF_SECOND),
            DAY_OF_MONTH(
                    ChronoField.DAY_OF_MONTH,
                    ChronoUnit.MONTHS,
                    ChronoField.HOUR_OF_DAY,
                    ChronoField.MINUTE_OF_HOUR,
                    ChronoField.SECOND_OF_MINUTE,
                    ChronoField.NANO_OF_SECOND),
            MONTH(
                    ChronoField.MONTH_OF_YEAR,
                    ChronoUnit.YEARS,
                    ChronoField.DAY_OF_MONTH,
                    ChronoField.HOUR_OF_DAY,
                    ChronoField.MINUTE_OF_HOUR,
                    ChronoField.SECOND_OF_MINUTE,
                    ChronoField.NANO_OF_SECOND),
            DAY_OF_WEEK(
                    ChronoField.DAY_OF_WEEK,
                    ChronoUnit.WEEKS,
                    ChronoField.HOUR_OF_DAY,
                    ChronoField.MINUTE_OF_HOUR,
                    ChronoField.SECOND_OF_MINUTE,
                    ChronoField.NANO_OF_SECOND);

            private final ChronoField field;

            private final ChronoUnit higherOrder;

            private final ChronoField[] lowerOrders;

            Type(ChronoField field, ChronoUnit higherOrder, ChronoField... lowerOrders) {
                this.field = field;
                this.higherOrder = higherOrder;
                this.lowerOrders = lowerOrders;
            }

            public int get(Temporal date) {
                return date.get(this.field);
            }

            public ValueRange range() {
                return this.field.range();
            }

            public int checkValidValue(int value) {
                if (this == DAY_OF_WEEK && value == 0) {
                    return value;
                } else {
                    try {
                        return this.field.checkValidIntValue(value);
                    } catch (DateTimeException ex) {
                        throw new IllegalArgumentException(ex.getMessage(), ex);
                    }
                }
            }

            public <T extends Temporal & Comparable<? super T>> T elapseUntil(T temporal, int goal) {
                int current = get(temporal);
                ValueRange range = temporal.range(this.field);
                if (current < goal) {
                    if (range.isValidIntValue(goal)) {
                        return cast(temporal.with(this.field, goal));
                    } else {
                        // goal is invalid, eg. 29th Feb, so roll forward
                        long amount = range.getMaximum() - current + 1;
                        return this.field.getBaseUnit().addTo(temporal, amount);
                    }
                } else {
                    long amount = goal + range.getMaximum() - current + 1 - range.getMinimum();
                    return this.field.getBaseUnit().addTo(temporal, amount);
                }
            }

            public <T extends Temporal & Comparable<? super T>> T rollForward(T temporal) {
                T result = this.higherOrder.addTo(temporal, 1);
                ValueRange range = result.range(this.field);
                return this.field.adjustInto(result, range.getMinimum());
            }

            public <T extends Temporal> T reset(T temporal) {
                for (ChronoField lowerOrder : this.lowerOrders) {
                    if (temporal.isSupported(lowerOrder)) {
                        temporal = lowerOrder.adjustInto(
                                temporal, temporal.range(lowerOrder).getMinimum());
                    }
                }
                return temporal;
            }

            @Override
            public String toString() {
                return this.field.toString();
            }
        }
    }

    static class BitsCronField extends CronField {

        private static final long MASK = 0xFFFFFFFFFFFFFFFFL;

        @Nullable
        private static BitsCronField zeroNanos = null;

        // we store at most 60 bits, for seconds and minutes, so a 64-bit long suffices
        private long bits;

        private BitsCronField(Type type) {
            super(type);
        }

        public static BitsCronField zeroNanos() {
            if (zeroNanos == null) {
                BitsCronField field = new BitsCronField(Type.NANO);
                field.setBit(0);
                zeroNanos = field;
            }
            return zeroNanos;
        }

        public static BitsCronField parseSeconds(String value) {
            return parseField(value, Type.SECOND);
        }

        public static BitsCronField parseMinutes(String value) {
            return BitsCronField.parseField(value, Type.MINUTE);
        }

        public static BitsCronField parseHours(String value) {
            return BitsCronField.parseField(value, Type.HOUR);
        }

        public static BitsCronField parseDaysOfMonth(String value) {
            return parseDate(value, Type.DAY_OF_MONTH);
        }

        public static BitsCronField parseMonth(String value) {
            return BitsCronField.parseField(value, Type.MONTH);
        }

        public static BitsCronField parseDaysOfWeek(String value) {
            BitsCronField result = parseDate(value, Type.DAY_OF_WEEK);
            if (result.getBit(0)) {
                // cron supports 0 for Sunday; we use 7 like java.time
                result.setBit(7);
                result.clearBit(0);
            }
            return result;
        }

        private static BitsCronField parseDate(String value, BitsCronField.Type type) {
            if (value.equals("?")) {
                value = "*";
            }
            return BitsCronField.parseField(value, type);
        }

        private static BitsCronField parseField(String value, Type type) {
            if (Utility.isBlank(value)) {
                throw new RedkaleException("Value must not be empty");
            }
            if (type == null) {
                throw new RedkaleException("Type must not be null");
            }
            try {
                BitsCronField result = new BitsCronField(type);
                String[] fields = CronField.delimitedListToStringArray(value, ",");
                for (String field : fields) {
                    int slashPos = field.indexOf('/');
                    if (slashPos == -1) {
                        ValueRange range = parseRange(field, type);
                        result.setBits(range);
                    } else {
                        String rangeStr = field.substring(0, slashPos);
                        String deltaStr = field.substring(slashPos + 1);
                        ValueRange range = parseRange(rangeStr, type);
                        if (rangeStr.indexOf('-') == -1) {
                            range = ValueRange.of(
                                    range.getMinimum(), type.range().getMaximum());
                        }
                        int delta = Integer.parseInt(deltaStr);
                        if (delta <= 0) {
                            throw new IllegalArgumentException("Incrementer delta must be 1 or higher");
                        }
                        result.setBits(range, delta);
                    }
                }
                return result;
            } catch (DateTimeException | IllegalArgumentException ex) {
                String msg = ex.getMessage() + " '" + value + "'";
                throw new IllegalArgumentException(msg, ex);
            }
        }

        private static ValueRange parseRange(String value, Type type) {
            if (value.equals("*")) {
                return type.range();
            } else {
                int hyphenPos = value.indexOf('-');
                if (hyphenPos == -1) {
                    int result = type.checkValidValue(Integer.parseInt(value));
                    return ValueRange.of(result, result);
                } else {
                    int min = Integer.parseInt(value, 0, hyphenPos, 10);
                    int max = Integer.parseInt(value, hyphenPos + 1, value.length(), 10);
                    min = type.checkValidValue(min);
                    max = type.checkValidValue(max);
                    if (type == Type.DAY_OF_WEEK && min == 7) {
                        // If used as a minimum in a range, Sunday means 0 (not 7)
                        min = 0;
                    }
                    return ValueRange.of(min, max);
                }
            }
        }

        @Nullable
        @Override
        public <T extends Temporal & Comparable<? super T>> T nextOrSame(T temporal) {
            int current = type().get(temporal);
            int next = nextSetBit(current);
            if (next == -1) {
                temporal = type().rollForward(temporal);
                next = nextSetBit(0);
            }
            if (next == current) {
                return temporal;
            } else {
                int count = 0;
                current = type().get(temporal);
                while (current != next && count++ < CronExpression.MAX_ATTEMPTS) {
                    temporal = type().elapseUntil(temporal, next);
                    current = type().get(temporal);
                    next = nextSetBit(current);
                    if (next == -1) {
                        temporal = type().rollForward(temporal);
                        next = nextSetBit(0);
                    }
                }
                if (count >= CronExpression.MAX_ATTEMPTS) {
                    return null;
                }
                return type().reset(temporal);
            }
        }

        boolean getBit(int index) {
            return (this.bits & (1L << index)) != 0;
        }

        private int nextSetBit(int fromIndex) {
            long result = this.bits & (MASK << fromIndex);
            if (result != 0) {
                return Long.numberOfTrailingZeros(result);
            } else {
                return -1;
            }
        }

        private void setBits(ValueRange range) {
            if (range.getMinimum() == range.getMaximum()) {
                setBit((int) range.getMinimum());
            } else {
                long minMask = MASK << range.getMinimum();
                long maxMask = MASK >>> -(range.getMaximum() + 1);
                this.bits |= (minMask & maxMask);
            }
        }

        private void setBits(ValueRange range, int delta) {
            if (delta == 1) {
                setBits(range);
            } else {
                for (int i = (int) range.getMinimum(); i <= range.getMaximum(); i += delta) {
                    setBit(i);
                }
            }
        }

        private void setBit(int index) {
            this.bits |= (1L << index);
        }

        private void clearBit(int index) {
            this.bits &= ~(1L << index);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(this.bits);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BitsCronField)) {
                return false;
            }
            BitsCronField other = (BitsCronField) o;
            return type() == other.type() && this.bits == other.bits;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(type().toString());
            builder.append(" {");
            int i = nextSetBit(0);
            if (i != -1) {
                builder.append(i);
                i = nextSetBit(i + 1);
                while (i != -1) {
                    builder.append(", ");
                    builder.append(i);
                    i = nextSetBit(i + 1);
                }
            }
            builder.append('}');
            return builder.toString();
        }
    }

    static class CompositeCronField extends CronField {

        private final CronField[] fields;

        private final String value;

        private CompositeCronField(Type type, CronField[] fields, String value) {
            super(type);
            this.fields = fields;
            this.value = value;
        }

        public static CronField compose(CronField[] fields, Type type, String value) {
            if (fields == null || fields.length < 1) {
                throw new RedkaleException("Fields must not be empty");
            }
            if (Utility.isBlank(value)) {
                throw new RedkaleException("Value must not be empty");
            }
            if (fields.length == 1) {
                return fields[0];
            } else {
                return new CompositeCronField(type, fields, value);
            }
        }

        @Nullable
        @Override
        public <T extends Temporal & Comparable<? super T>> T nextOrSame(T temporal) {
            T result = null;
            for (CronField field : this.fields) {
                T candidate = field.nextOrSame(temporal);
                if (result == null || candidate != null && candidate.compareTo(result) < 0) {
                    result = candidate;
                }
            }
            return result;
        }

        @Override
        public int hashCode() {
            return this.value.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CompositeCronField)) {
                return false;
            }
            CompositeCronField other = (CompositeCronField) o;
            return type() == other.type() && this.value.equals(other.value);
        }

        @Override
        public String toString() {
            return type() + " '" + this.value + "'";
        }
    }
}
