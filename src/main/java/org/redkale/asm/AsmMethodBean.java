/*
 *
 */
package org.redkale.asm;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.redkale.annotation.Param;

/**
 * 存放方法的字节信息
 *
 * @see org.redkale.asm.AsmMethodBoost
 * @since 2.8.0
 */
public class AsmMethodBean {

    private List<AsmMethodParam> params;

    private int access;

    private String name;

    private String desc;

    private String signature;

    private String[] exceptions;

    public AsmMethodBean() {}

    public AsmMethodBean(int access, String name, String desc, String signature, String[] exceptions) {
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.signature = signature;
        this.exceptions = exceptions;
        this.params = new ArrayList<>();
    }

    public static AsmMethodBean get(Map<String, AsmMethodBean> map, Method method) {
        return map == null ? null : map.get(method.getName() + ":" + Type.getMethodDescriptor(method));
    }

    void removeEmptyNames() {
        if (params != null) {
            List<AsmMethodParam> dels = null;
            for (AsmMethodParam p : params) {
                if (" ".equals(p.getName())) {
                    if (dels == null) {
                        dels = new ArrayList<>();
                    }
                    dels.add(p);
                }
            }
            if (dels != null) {
                for (AsmMethodParam p : dels) {
                    params.remove(p);
                }
            }
        }
    }

    public List<String> fieldNameList() {
        return paramNameList(null);
    }

    public List<String> paramNameList(Method method) {
        if (params == null) {
            return new ArrayList<>();
        }
        int index = 0;
        Parameter[] ps = method == null ? null : method.getParameters();
        List<String> rs = new ArrayList<>(params.size());
        for (AsmMethodParam p : params) {
            Param pann = ps == null ? null : ps[index].getAnnotation(Param.class);
            rs.add(pann == null ? p.getName() : pann.value());
            index++;
        }
        return rs;
    }

    public String[] fieldNameArray() {
        return paramNameArray(null);
    }

    public String[] paramNameArray(Method method) {
        if (params == null) {
            return null;
        }
        Parameter[] ps = method == null ? null : method.getParameters();
        String[] rs = new String[params.size()];
        for (int i = 0; i < rs.length; i++) {
            Param pann = ps == null ? null : ps[i].getAnnotation(Param.class);
            rs[i] = pann == null ? params.get(i).getName() : pann.value();
        }
        return rs;
    }

    public List<AsmMethodParam> getParams() {
        return params;
    }

    public void setParams(List<AsmMethodParam> params) {
        this.params = params;
    }

    public int getAccess() {
        return access;
    }

    public void setAccess(int access) {
        this.access = access;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String[] getExceptions() {
        return exceptions;
    }

    public void setExceptions(String[] exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public String toString() {
        return "{params:" + params + ", access:" + access + ", name:" + name + ", desc:" + desc + ", signature:"
                + signature + ", exceptions:" + exceptions + '}';
    }
}
