/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.convert.json.JsonConvert;

/**
 * 序列化去掉引号的String对象。
 * <blockquote><pre>
 * 场景: JavaBean  bean = ... ;
 *        Map map = new HashMap();
 *        map.put("bean", a);
 *        records.add(map);
 *        records需要在后期序列化写入库。 但是在这期间bean的内部字段值可能就变化了，会导致入库时并不是records.add的快照信息。
 *        所以需要使用StringConvertWrapper：
 *        Map map = new HashMap();
 *        map.put("bean", new StringConvertWrapper(bean.toString()));
 *        records.add(map);
 *        这样既可以保持快照又不会在bean的值上面多一层引号。
 * </pre></blockquote>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class StringConvertWrapper {

    protected String value;

    public StringConvertWrapper() {
    }

    public StringConvertWrapper(String value) {
        this.value = value;
    }

    public static StringConvertWrapper create(Object value) {
        return create(JsonConvert.root(), value);
    }

    public static StringConvertWrapper create(TextConvert convert, Object value) {
        if (value == null) return new StringConvertWrapper(null);
        if (value instanceof String) return new StringConvertWrapper((String) value);
        return new StringConvertWrapper(convert.convertTo(value));
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

}
