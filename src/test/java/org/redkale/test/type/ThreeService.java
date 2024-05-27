/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.type;

/**
 * @author zhangjx
 * @param <K>
 * @param <ER>
 * @param <EB>
 */
public class ThreeService<K extends CharSequence, ER extends ThreeRound, EB extends ThreeBean>
		extends OneService<ER, EB> {

	public String key(K key) {
		return "" + key;
	}
}
