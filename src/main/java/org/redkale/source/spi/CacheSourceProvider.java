/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source.spi;

import org.redkale.source.CacheSource;
import org.redkale.util.*;

/**
 *
 * 自定义的CacheSource加载器, 如果标记&#64;Priority加载器的优先级需要大于1000， 1000以下预留给官方加载器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.5.0
 */
public interface CacheSourceProvider extends InstanceProvider<CacheSource> {

}
