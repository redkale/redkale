/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import org.redkale.util.AnyValue;

/**
 * 
 * 自定义的DataSource加载器, 如果标记&#64;Priority加载器的优先级需要大于1000， 1000以下预留给官方加载器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.5.0
 */
public interface DataSourceProvider {

    public boolean acceptsConf(AnyValue config);

    public Class<? extends DataSource> sourceClass();
}
