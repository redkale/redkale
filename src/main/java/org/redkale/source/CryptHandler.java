/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

/**
 * 字段加密解密接口
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.0.0
 * @param <S> 加密的字段类型
 * @param <D> 加密后的数据类型
 */
public interface CryptHandler<S, D> {

    /**
     * 加密
     *
     * @param value 加密前的字段值
     *
     * @return 加密后的字段值
     */
    public D encrypt(S value);

    /**
     * 解密
     *
     * @param value 加密的字段值
     *
     * @return 解密后的字段值
     */
    public S decrypt(D value);
}
