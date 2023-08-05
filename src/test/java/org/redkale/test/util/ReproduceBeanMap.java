/*
 *
 */
package org.redkale.test.util;

import java.util.Map;
import org.redkale.util.Reproduce;

/**
 *
 * @author zhangjx
 */
public class ReproduceBeanMap implements Reproduce<Map<String, Object>, TestInterface> {

    @Override
    public Map apply(Map<String, Object> dest, TestInterface src) {
        Object v;
        dest.put("id", src.getId());

        v = src.getMap();
        if (v != null) {
            dest.put("map", v);
        }
        return dest;
    }

    public Map run(Map<String, Object> dest, TestBean src) {
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
