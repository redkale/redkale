/*
 *
 */
package org.redkale.test.util;

import java.util.*;
import org.redkale.util.*;

/**
 * @author zhangjx
 */
public class CopierMapBean implements Copier<HashMap, TestBean> {

    @Override
    public TestBean apply(HashMap src, TestBean dest) {
        src.forEach((k, v) -> {
            if ("id".equals(k) && v != null) {
                dest.setId(Utility.convertValue(int.class, v));
            } else if ("map".equals(k)) {
                dest.setMap(Utility.convertValue(Map.class, v));
            } else if ("map2".equals(k) && v != null) {
                dest.setMap(Utility.convertValue(Map.class, v));
            } else if ("time".equals(k)) {
                dest.time = Utility.convertValue(long.class, v);
            } else {

            }
        });
        return dest;
    }
}
