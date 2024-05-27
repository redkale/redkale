/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.type;

import java.lang.reflect.*;
import org.junit.jupiter.api.*;
import org.redkale.util.TypeToken;

/** @author zhangjx */
public class TypeTokenTest {

	private boolean main;

	public static void main(String[] args) throws Throwable {
		TypeTokenTest test = new TypeTokenTest();
		test.main = true;
		test.run();
	}

	@Test
	public void run() throws Exception {
		Class declaringClass = ThreeService.class;
		ParameterizedType declaringType = (ParameterizedType) declaringClass.getGenericSuperclass();
		System.out.println("getRawType:" + declaringType.getRawType());
		TypeVariable argType0 = (TypeVariable) declaringType.getActualTypeArguments()[0];
		System.out.println("argType0.getBounds[0]:" + argType0.getBounds()[0]);

		for (Method method : declaringClass.getMethods()) {
			if (!"run".equals(method.getName())) continue;
			if (!main)
				Assertions.assertEquals(
						ThreeRound.class,
						TypeToken.getGenericType(method.getGenericParameterTypes()[0], declaringClass));
			System.out.println("返回值应该是: " + ThreeRound.class);
			System.out.println(
					"返回值结果是: " + TypeToken.getGenericType(method.getGenericParameterTypes()[0], declaringClass));
			break;
		}
	}
}
