/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

/**
 * 序列化去掉引号的String对象。
 * <blockquote><pre>
 * 场景: JavaBean  a = ... ;
 *        Map map = new HashMap();
 *        map.put("bean", a);
 *        records.add(map);
 *        records需要在后期序列化写入库。 但是在这期间a的内部字段值可能就变化了，会导致入库时并不是records.add的快照信息。
 *        所以需要使用StringConvertWrapper：
 *        Map map = new HashMap();
 *        map.put("bean", new StringConvertWrapper(a.toString()));
 *        records.add(map);
 *        这样序列化写库时不需要在bean的值上面多一层引号。
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

    public static StringConvertWrapper create(String value) {
        return new StringConvertWrapper(value);
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
