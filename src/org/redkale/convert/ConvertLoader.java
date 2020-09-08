/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

/**
 * Convert的扩展实现类加载器
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.2.0
 */
public interface ConvertLoader {

    public ConvertType type();

    public Convert convert();
}
