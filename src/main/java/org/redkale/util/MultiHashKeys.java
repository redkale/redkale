/*
 *
 */
package org.redkale.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** @author zhangjx */
class MultiHashKeys {

    public static MultiHashKey create(String[] paramNames, String key) {
        Objects.requireNonNull(key, "key for " + MultiHashKey.class.getSimpleName() + " is null");
        if (key.indexOf('{') < 0) { // 字符串常量
            return new StringKey(key);
        } else {
            Objects.requireNonNull(paramNames, "paramNames for " + MultiHashKey.class.getSimpleName() + " is null");
            char last = 0;
            boolean paraming = false;
            char[] chars = key.toCharArray();
            StringBuilder sb = new StringBuilder();
            List<MultiHashKey> list = new ArrayList<>();
            for (int i = 0; i < chars.length; i++) {
                char ch = chars[i];
                if (ch == '{') {
                    if (paraming) {
                        throw new RedkaleException(MultiHashKey.class.getSimpleName() + " parse error, key: " + key);
                    }
                    if (last == '#') {
                        String name = sb.substring(0, sb.length() - 1);
                        if (!name.isEmpty()) {
                            list.add(new StringKey(name));
                        }
                        sb.delete(0, sb.length());
                        paraming = true;
                    } else if (last == '\\') {
                        sb.deleteCharAt(sb.length() - 1);
                        sb.append(ch);
                    } else {
                        sb.append(ch);
                    }
                } else if (ch == '}' && last == '\\') {
                    sb.deleteCharAt(sb.length() - 1);
                    sb.append(ch);
                } else if (ch == '}' && paraming) {
                    String name = sb.toString();
                    sb.delete(0, sb.length());
                    if (name.indexOf('.') > 0) {
                        list.add(new ParamsKey(paramNames, name));
                    } else {
                        list.add(new ParamKey(paramNames, name));
                    }
                    paraming = false;
                } else {
                    sb.append(ch);
                }
                last = ch;
            }
            if (sb.length() > 0) {
                list.add(new StringKey(sb.toString()));
            }
            if (list.size() == 1) {
                return list.get(0);
            }
            return new ArrayKey(list.toArray(new MultiHashKey[list.size()]));
        }
    }

    static class ArrayKey implements MultiHashKey {

        private final MultiHashKey[] keys;

        public ArrayKey(MultiHashKey[] keys) {
            this.keys = keys;
        }

        @Override
        public String keyFor(Object... args) {
            StringBuilder sb = new StringBuilder();
            for (MultiHashKey key : keys) {
                sb.append(key.keyFor(args));
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return ArrayKey.class.getSimpleName() + Arrays.toString(keys);
        }
    }

    static class ParamsKey implements MultiHashKey {

        private static final ConcurrentHashMap<String, Attribute> attrCache = new ConcurrentHashMap<>();

        private final int index;

        private final String fullField;

        private final String[] fields;

        public ParamsKey(int index, String fullField) {
            this.index = index;
            this.fullField = fullField;
            this.fields = fullField.split("\\.");
        }

        public ParamsKey(String[] paramNames, String fullField) {
            int rs = -1;
            for (int i = 0; i < paramNames.length; i++) {
                if (fullField.startsWith(paramNames[i] + '.')) {
                    rs = i;
                    break;
                }
            }
            if (rs < 0) {
                throw new RedkaleException(fullField + " not found in " + Arrays.toString(paramNames));
            }
            this.index = rs;
            this.fullField = fullField;
            this.fields = fullField.split("\\.");
        }

        @Override
        public String keyFor(Object... args) {
            return String.valueOf(get(args[index]));
        }

        private Object get(Object val) {
            if (val == null) {
                return val;
            }
            String[] subs = fields;
            for (int i = 1; i < subs.length; i++) {
                String fieldName = subs[i];
                Class clz = val.getClass();
                Attribute attr = attrCache.computeIfAbsent(
                        clz.getName() + ":" + fieldName, k -> Attribute.create(clz, fieldName));
                val = attr.get(val);
                if (val == null) {
                    return val;
                }
            }
            return val;
        }

        @Override
        public String toString() {
            return ParamsKey.class.getSimpleName() + "{field: " + fullField + ", index: " + index + "}";
        }
    }

    static class ParamKey implements MultiHashKey {

        private final int index;

        private final String field;

        public ParamKey(int index, String field) {
            this.index = index;
            this.field = field;
        }

        public ParamKey(String[] paramNames, String field) {
            int rs = -1;
            for (int i = 0; i < paramNames.length; i++) {
                if (field.equalsIgnoreCase(paramNames[i])) {
                    rs = i;
                    break;
                }
            }
            if (rs < 0) {
                throw new RedkaleException(field + " not found in " + Arrays.toString(paramNames));
            }
            this.index = rs;
            this.field = field;
        }

        @Override
        public String keyFor(Object... args) {
            return String.valueOf(args[index]);
        }

        @Override
        public String toString() {
            return ParamKey.class.getSimpleName() + "{field: " + field + ", index: " + index + "}";
        }
    }

    static class StringKey implements MultiHashKey {

        private final String key;

        public StringKey(String key) {
            this.key = key;
        }

        @Override
        public String keyFor(Object... args) {
            return key;
        }

        @Override
        public String toString() {
            return StringKey.class.getSimpleName() + "{key: " + key + "}";
        }
    }

    private MultiHashKeys() {
        // do nothing
    }
}
