/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

/**
 * 简单的byte[]数据接口。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
public interface ByteTuple {

    public byte[] content();

    public int offset();

    public int length();
}
