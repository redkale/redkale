/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.spi;

import org.redkale.convert.Convert;
import org.redkale.convert.ConvertType;

/**
 * Convert的扩展实现类加载器, 通过此类可以创建自定义的序列化格式，例如：protobuf、xmlbean
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.5.0
 */
public interface ConvertProvider {

    public ConvertType type();

    public Convert convert();
}
