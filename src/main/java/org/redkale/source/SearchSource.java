/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.util.concurrent.CompletableFuture;

/**
 * 搜索引擎的数据源， 接口与DataSource基本一致。 <br>
 * 返回类型为CompletableFuture的接口为异步接口
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.4.0
 */
public interface SearchSource extends DataSource {

	// 不存在分表时用此方法
	default <T> int updateMapping(final Class<T> clazz) {
		return updateMapping(clazz, null);
	}

	// 不存在分表时用此方法
	default <T> CompletableFuture<Integer> updateMappingAsync(final Class<T> clazz) {
		return updateMappingAsync(clazz, null);
	}

	// 存在分表时用此方法
	public <T> int updateMapping(final Class<T> clazz, String table);

	// 存在分表时用此方法
	public <T> CompletableFuture<Integer> updateMappingAsync(final Class<T> clazz, String table);
}
