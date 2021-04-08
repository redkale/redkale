/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.util.function.*;
import org.redkale.util.ByteArray;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
public interface ClientRequest extends BiConsumer<ClientConnection, ByteArray> {

    public static class ClientBytesRequest implements ClientRequest {

        protected byte[] bytes;

        public ClientBytesRequest(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public void accept(ClientConnection conn, ByteArray array) {
            array.put(bytes);
        }

    }
}
