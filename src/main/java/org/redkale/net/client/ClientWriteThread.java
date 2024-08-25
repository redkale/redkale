/*

*/

package org.redkale.net.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 输出队列线程
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class ClientWriteThread extends Thread {

    protected final LinkedBlockingQueue<ClientFuture> writeQueue = new LinkedBlockingQueue();

    protected ClientWriteThread() {
        // do nothing
    }

    public void offer(ClientFuture respFuture) {
        this.writeQueue.add(Objects.requireNonNull(respFuture));
    }

    @Override
    public void run() {
        final List<ClientFuture> list = new ArrayList<>();
        while (true) {
            try {
                ClientFuture respFuture = this.writeQueue.take();
                if (respFuture == ClientFuture.NIL) {
                    return;
                }
                boolean over = false;
                list.clear();
                list.add(respFuture);
                ClientConnection conn = respFuture.conn;
                int max = conn.getMaxPipelines();
                while (--max > 0 && (respFuture = this.writeQueue.poll()) != null) {
                    if (respFuture == ClientFuture.NIL) {
                        over = true;
                        break;
                    } else if (respFuture.conn == conn) {
                        list.add(respFuture);
                    }
                }
                conn.sendRequestToChannel(ClientFuture.array(list));
                list.clear();
                if (over) {
                    return;
                }
            } catch (InterruptedException ie) {
                break;
            } catch (Throwable e) {
                // do nothing
            }
        }
    }

    public void close() {
        this.writeQueue.add(ClientFuture.NIL);
        this.interrupt();
    }
}
