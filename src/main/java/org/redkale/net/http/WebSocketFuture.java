/*
 *
 */
package org.redkale.net.http;

import java.util.*;
import java.util.concurrent.*;
import org.redkale.net.WorkThread;

/**
 * WebSocket连接的IO写线程
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class WebSocketFuture extends CompletableFuture<Integer> implements Runnable {

	WebSocketPacket[] packets;

	WebSocket websocket;

	WorkThread workThread;

	ScheduledFuture timeout;

	WebSocketFuture(WorkThread workThread, WebSocket websocket, WebSocketPacket... packets) {
		super();
		this.workThread = workThread;
		this.websocket = websocket;
		this.packets = packets;
	}

	void cancelTimeout() {
		if (timeout != null) {
			timeout.cancel(true);
		}
	}

	@Override // JDK9+
	public WebSocketFuture newIncompleteFuture() {
		WebSocketFuture future = new WebSocketFuture(workThread, websocket, packets);
		future.timeout = timeout;
		return future;
	}

	@Override
	public void run() {
		TimeoutException ex = new TimeoutException("packets: " + Arrays.toString(packets));
		workThread.runWork(() -> completeExceptionally(ex));
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "_" + Objects.hash(this) + "{websocket = " + websocket + ", packets = "
				+ Arrays.toString(packets) + "}";
	}
}
