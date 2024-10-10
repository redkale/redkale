/*
 *
 */
package org.redkale.asm;

import org.redkale.util.TypeToken;

/**
 * 存放方法参数的字节信息
 *
 * @see org.redkale.asm.AsmMethodBean
 * @see org.redkale.asm.AsmMethodBoost
 * @since 2.8.0
 */
public class AsmMethodParam {

    private String name;

    private String description;

    private String signature;

    public AsmMethodParam() {}

    public AsmMethodParam(String name) {
        this.name = name;
    }

    public AsmMethodParam(String name, String description, String signature) {
        this.name = name;
        this.description = description;
        this.signature = signature;
    }

    public String description(java.lang.reflect.Type type) {
        return description == null ? Type.getDescriptor(TypeToken.typeToClass(type)) : description;
    }

    public String signature(java.lang.reflect.Type type) {
        return signature;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "{name:" + name + ", description:" + description + ", signature:" + signature + '}';
    }
}
