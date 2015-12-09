/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;

/**
 * 便于操作的HashMap类
 *
 * @author zhangjx
 */
public class ObjectNode extends HashMap<String, Object> {

    public static ObjectNode create(String key, Object value) {
        ObjectNode node = new ObjectNode();
        node.put(key, value);
        return node;
    }

    public ObjectNode appand(String key, Object value) {
        this.put(key, value);
        return this;
    }
}
