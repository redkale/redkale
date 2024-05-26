/*
 *
 */
package org.redkale.test.util;

import java.util.Map;
import org.redkale.util.Copier;

/** @author zhangjx */
public class CopierBeanMap implements Copier<TestInterface, Map<String, Object>> {

    @Override
    public Map apply(TestInterface src, Map<String, Object> dest) {
        Object v;
        dest.put("id", src.getId());

        v = src.getMap();
        if (v != null) {
            dest.put("map", v);
        }
        return dest;
    }

    public Map run(TestBean src, Map<String, Object> dest) {
        Object v;

        v = src.getName();
        if (v != null) {
            dest.put("name", v);
        }

        v = src.time;
        if (v != null) {
            dest.put("time", v);
        }

        dest.put("id", src.getId());

        v = src.getName();
        if (v != null) {
            dest.put("name", v);
        }
        //
        return dest;
    }
}
