/*

*/

package org.redkale.asm;

import org.redkale.convert.json.JsonConvert;

/**
 * 存放新方法的信息
 *
 * @since 2.8.0
 */
public class AsmNewMethod {

    private String methodName;

    private int methodAccs;

    public AsmNewMethod() {}

    public AsmNewMethod(String newName, int newAccs) {
        this.methodName = newName;
        this.methodAccs = newAccs;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public int getMethodAccs() {
        return methodAccs;
    }

    public void setMethodAccs(int methodAccs) {
        this.methodAccs = methodAccs;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
