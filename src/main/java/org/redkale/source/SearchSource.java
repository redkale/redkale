/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.util.concurrent.CompletableFuture;

/**
 *
 * 搜索引擎的数据源， 接口与DataSource基本一致。  <br>
 * 返回类型为CompletableFuture的接口为异步接口
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.4.0
 */
public interface SearchSource extends DataSource {

    public <T> int updateMapping(final Class<T> clazz);

    public <T> CompletableFuture<Integer> updateMappingAsync(final Class<T> clazz);
}
