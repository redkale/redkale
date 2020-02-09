/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.type;

import java.lang.reflect.Method;
import org.redkale.util.TypeToken;

/**
 *
 * @author zhangjx
 */
public class TypeTokenTest {

    public static void main(String[] args) throws Throwable {
        Class declaringClass = TwoService.class;
        for (Method method : declaringClass.getMethods()) {
            if (!"run".equals(method.getName())) continue;
            System.out.println("返回值应该是: " + TwoRound.class);
            System.out.println("返回值结果是: " + TypeToken.getGenericType(method.getGenericParameterTypes()[0], declaringClass));
            break;
        }
    }

}
