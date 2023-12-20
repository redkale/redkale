/*
 *
 */
package org.redkale.test.cache;

import java.lang.reflect.Method;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.Type;

/**
 *
 * @author zhangjx
 */
public class TwoService extends BaseService {

    @Override
    protected void run2() {
    }

    @Override
    public void run3() {
    }

    public static void main(String[] args) throws Throwable {
        System.out.println("-------------------------------");
        for (Method m : TwoService.class.getDeclaredMethods()) {
            System.out.println(m);
        }
        System.out.println("-------------------------------");
        for (Method m : SimpleService.class.getDeclaredMethods()) {
            System.out.println(m);
        }
        System.out.println("-------------------------------");
        for (Method m : BaseService.class.getDeclaredMethods()) {
            System.out.println(m);
            if(m.getName().equals("toMap")) {
            System.out.println("张颠三倒四： " + Type.getType(m).getInternalName());
            System.out.println("张颠三倒四： " + Type.getType(m).getDescriptor());
            System.out.println("张颠三倒四： " + Type.getType(m).getClassName());
            }
        }
        System.out.println("-------------------------------");
        System.out.println(AsmMethodBoost.getMethodBeans(BaseService.class));
    }
}
