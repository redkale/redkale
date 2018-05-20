/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

/**
 * 自定义的DataSource加载器
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface SourceLoader {

    public String dbtype();

    public Class<? extends DataSource> dataSourceClass();
}
