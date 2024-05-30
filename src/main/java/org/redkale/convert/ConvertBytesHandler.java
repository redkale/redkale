/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.util.function.Consumer;

/**
 * convertToBytes系列的方法的回调
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
public interface ConvertBytesHandler {

    <A> void completed(byte[] bs, int offset, int length, Consumer<A> callback, A attachment);
}
