/*
 */
package org.redkale.net.http;

import org.redkale.util.AnyValue;

/**
 * rpc鉴权验证器 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public interface HttpRpcAuthenticator {

	/**
	 * 初始化方法
	 *
	 * @param config 配置参数
	 */
	default void init(AnyValue config) {}

	/**
	 * 成功返回true， 不成功返回false，且需要response.finish()输出失败的信息， 比如404
	 *
	 * @param request HttpRequest
	 * @param response HttpResponse
	 * @return 是否验证成功
	 */
	public boolean auth(HttpRequest request, HttpResponse response);

	/**
	 * 销毁方法
	 *
	 * @param config 配置参数
	 */
	default void destroy(AnyValue config) {}
}
