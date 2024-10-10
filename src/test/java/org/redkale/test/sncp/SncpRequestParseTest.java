/*
 *
 */
package org.redkale.test.sncp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Logger;
import org.junit.jupiter.api.*;
import org.redkale.net.*;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.sncp.*;
import org.redkale.util.*;

/** @author zhangjx */
public class SncpRequestParseTest {

    public static void main(String[] args) throws Throwable {
        SncpRequestParseTest test = new SncpRequestParseTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        InetSocketAddress sncpAddress = new InetSocketAddress("127.0.0.1", 3389);
        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 3344);
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        SncpClient client = new SncpClient(
                "test", asyncGroup, "0", sncpAddress, new ClientAddress(remoteAddress), "TCP", Utility.cpus(), 16);
        SncpClientConnection conn = client.createClientConnection(asyncGroup.newTCPClientConnection());

        SncpContext.SncpContextConfig config = new SncpContext.SncpContextConfig();
        config.logger = Logger.getLogger(SncpRequestParseTest.class.getSimpleName());
        config.serverAddress = sncpAddress;
        config.maxHeader = 16 * 1024;
        config.maxBody = 1024 * 1024 * 1024;
        SncpContext context = new SncpContext(config);

        SncpHeader header = SncpHeader.create(sncpAddress, Uint128.ZERO, "", Uint128.ZERO, "");
        SncpClientRequest clientRequest = new SncpClientRequest();
        ByteArray writeArray = new ByteArray();
        clientRequest.prepare(header, 1, "aa", new byte[20]);
        clientRequest.writeTo(conn, writeArray);
        byte[] bs = writeArray.getBytes();
        int headerSize = SncpHeader.calcHeaderSize(clientRequest);
        System.out.println("整个sncp请求长度: " + bs.length + "." + Arrays.toString(bs));
        System.out.println("                           " + Arrays.toString(Arrays.copyOfRange(bs, 2, bs.length)));

        SncpRequestTest request = new SncpRequestTest(context);
        Assertions.assertEquals(1, request.readHeader(ByteBuffer.wrap(Arrays.copyOfRange(bs, 0, 1)), -1));
        Assertions.assertEquals(headerSize - 2, request.readHeader(ByteBuffer.wrap(Arrays.copyOfRange(bs, 1, 2)), -1));
        Assertions.assertEquals(0, request.readHeader(ByteBuffer.wrap(Arrays.copyOfRange(bs, 2, bs.length)), -1));
        Assertions.assertEquals("aa", request.getHeader().getTraceid());

        System.out.println("测试第二段");
        request = new SncpRequestTest(context);
        Assertions.assertEquals(1, request.readHeader(ByteBuffer.wrap(Arrays.copyOfRange(bs, 0, 1)), -1));
        Assertions.assertEquals(headerSize - 2, request.readHeader(ByteBuffer.wrap(Arrays.copyOfRange(bs, 1, 2)), -1));
        Assertions.assertEquals(
                headerSize - headerSize / 2,
                request.readHeader(ByteBuffer.wrap(Arrays.copyOfRange(bs, 2, headerSize / 2)), -1));
        Assertions.assertEquals(
                0, request.readHeader(ByteBuffer.wrap(Arrays.copyOfRange(bs, headerSize / 2, bs.length)), -1));
        Assertions.assertEquals("aa", request.getHeader().getTraceid());
    }

    public static class SncpRequestTest extends SncpRequest {

        protected SncpRequestTest(SncpContext context) {
            super(context);
        }

        @Override
        protected int readHeader(ByteBuffer buffer, int pipelineHeaderLength) {
            return super.readHeader(buffer, pipelineHeaderLength);
        }
    }
}
