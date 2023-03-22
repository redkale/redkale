/*
 *
 */
package org.redkale.test.sncp;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.net.AsyncIOGroup;
import org.redkale.net.client.*;
import org.redkale.net.sncp.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class SncpClientCodecTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        SncpClientCodecTest test = new SncpClientCodecTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        InetSocketAddress sncpAddress = new InetSocketAddress("127.0.0.1", 3389);
        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 3344);
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        SncpClient client = new SncpClient("test", asyncGroup, sncpAddress, new ClientAddress(remoteAddress), "TCP", Utility.cpus(), 16);
        SncpClientConnection conn = client.createClientConnection(1, asyncGroup.newTCPClientConnection());
        SncpClientCodec codec = new SncpClientCodec(conn);
        List respResults = new ArrayList();
        try {
            Field respResultsField = ClientCodec.class.getDeclaredField("respResults");
            respResultsField.setAccessible(true);
            respResults = (List) respResultsField.get(codec);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //----------------------------------------------
        ByteBuffer realBuf;
        {
            SncpHeader header = new SncpHeader(sncpAddress, Uint128.ZERO, Uint128.ZERO);
            SncpClientRequest request = new SncpClientRequest();
            ByteArray writeArray = new ByteArray();
            request.prepare(header, 1, "", new ByteArray().putPlaceholder(20));
            System.out.println("request.1 = " + request);
            writeArray.put(new byte[SncpHeader.HEADER_SIZE]);
            request.writeTo(conn, writeArray);
            request.prepare(header, 2, "", new ByteArray().putPlaceholder(25));
            System.out.println("request.2 = " + request);
            writeArray.put(new byte[SncpHeader.HEADER_SIZE]);
            request.writeTo(conn, writeArray);
            System.out.println(writeArray.getBytes().length);
            realBuf = ByteBuffer.wrap(writeArray.getBytes());
        }
        System.out.println("sncp.realBuf = " + realBuf.remaining());
        codec.decodeMessages(realBuf, new ByteArray());
        System.out.println("respResults.size = " + respResults.size());
        Assertions.assertEquals(2, respResults.size());
    }
}
