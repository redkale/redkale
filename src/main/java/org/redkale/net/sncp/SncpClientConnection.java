/*
 *
 */
package org.redkale.net.sncp;

import org.redkale.net.AsyncConnection;
import org.redkale.net.client.*;
import org.redkale.util.ObjectPool;

/**
 * client版连接
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class SncpClientConnection extends ClientConnection<SncpClientRequest, SncpClientResult> {

	private final ObjectPool<SncpClientRequest> requestPool;

	public SncpClientConnection(SncpClient client, AsyncConnection channel) {
		super(client, channel);
		requestPool = ObjectPool.createUnsafePool(
				Thread.currentThread(),
				256,
				ObjectPool.createSafePool(
						256, t -> new SncpClientRequest(), SncpClientRequest::prepare, SncpClientRequest::recycle));
	}

	@Override
	protected ClientCodec createCodec() {
		return new SncpClientCodec(this);
	}

	protected void offerResult(SncpClientRequest req, SncpClientResult rs) {
		SncpClientCodec c = getCodec();
		c.offerResult(rs);
		requestPool.accept(req);
	}
}
