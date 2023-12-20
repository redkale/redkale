/*
 *
 */
package org.redkale.util;

import java.util.Objects;

/**
 *
 * @author zhangjx
 */
class CombinedKeys {

    public static CombinedKey create(Class[] paramTypes, String[] paramNames, String key) {
        Objects.requireNonNull(key, "key for " + CombinedKey.class.getSimpleName() + " is null");
        if ((paramTypes != null && paramNames != null && paramTypes.length != paramNames.length)
            || (paramTypes == null && paramNames != null)
            || (paramTypes != null && paramNames == null)) {
            throw new IllegalArgumentException("paramTypes.length and paramNames.length is inconsistent");
        }
        if (key.indexOf('{') < 0) {
            return new StringDynamicKey(key);
        } else {
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    if (key.equalsIgnoreCase("#{" + paramNames[i] + "}")) {
                        return new ParamDynamicKey(i);
                    }
                }
            }
            return new CombinedDynamicKey(paramTypes, paramNames, key);
        }
    }

    static class CombinedDynamicKey implements CombinedKey {

        private final CombinedKey[] keys;

        public CombinedDynamicKey(Class[] paramTypes, String[] paramNames, String key) {
            this.keys = new CombinedKey[0];
        }

        @Override
        public String keyFor(Object... args) {
            StringBuilder sb = new StringBuilder();
            for (CombinedKey key : keys) {
                sb.append(key.keyFor(args));
            }
            return sb.toString();
        }

    }

    static class ParamDynamicKey implements CombinedKey {

        private final int index;

        public ParamDynamicKey(int index) {
            this.index = index;
        }

        @Override
        public String keyFor(Object... args) {
            return String.valueOf(args[index]);
        }

    }

    static class StringDynamicKey implements CombinedKey {

        private final String key;

        public StringDynamicKey(String key) {
            this.key = key;
        }

        @Override
        public String keyFor(Object... args) {
            return key;
        }
    }

    private CombinedKeys() {
        //do nothing
    }

}
