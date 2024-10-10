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

/** @author zhangjx */
public class SncpClientCodecTest {

    public static void main(String[] args) throws Throwable {
        SncpClientCodecTest test = new SncpClientCodecTest();
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
        SncpClientCodec codec = new SncpClientCodec(conn);
        List respResults = new ArrayList();
        try {
            Field respResultsField = ClientCodec.class.getDeclaredField("respResults");
            respResultsField.setAccessible(true);
            respResults = (List) respResultsField.get(codec);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ByteBuffer realBuf;
        // ----------------------------------------------
        respResults.clear();
        {
            SncpHeader header = SncpHeader.create(sncpAddress, Uint128.ZERO, "", Uint128.ZERO, "");
            SncpClientRequest request = new SncpClientRequest();
            ByteArray writeArray1 = new ByteArray();
            request.prepare(header, 1, "aa", new byte[20]);
            request.writeTo(conn, writeArray1);
            System.out.println("request.1 = " + request);
            System.out.println("headerSize = " + SncpHeader.calcHeaderSize(request) + ", arraySzie = "
                    + writeArray1.getBytes().length);
            ByteArray writeArray2 = new ByteArray();
            request.prepare(header, 2, "bb", new byte[25]);
            request.writeTo(conn, writeArray2);
            System.out.println("request.2 = " + request);
            System.out.println("headerSize = " + SncpHeader.calcHeaderSize(request) + ", arraySzie = "
                    + writeArray2.getBytes().length);
            writeArray1.put(writeArray2);
            realBuf = ByteBuffer.wrap(writeArray1.getBytes());
        }
        System.out.println("sncp.realBuf = " + realBuf.remaining());
        codec.decodeMessages(realBuf, new ByteArray());
        System.out.println("respResults.size = " + respResults.size());
        Assertions.assertEquals(2, respResults.size());
        // ----------------------------------------------
        respResults.clear();
        {
            SncpHeader header = SncpHeader.create(sncpAddress, Uint128.ZERO, "", Uint128.ZERO, "");
            SncpClientRequest request = new SncpClientRequest();
            ByteArray writeArray1 = new ByteArray();
            request.prepare(header, 1, "", new byte[20]);
            request.writeTo(conn, writeArray1);
            System.out.println("request.1 = " + request);
            System.out.println("headerSize = " + SncpHeader.calcHeaderSize(request) + ", arraySzie = "
                    + writeArray1.getBytes().length);
            ByteArray writeArray2 = new ByteArray();
            request.prepare(header, 2, "", new byte[25]);
            request.writeTo(conn, writeArray2);
            System.out.println("request.2 = " + request);
            System.out.println("headerSize = " + SncpHeader.calcHeaderSize(request) + ", arraySzie = "
                    + writeArray2.getBytes().length);
            writeArray1.put(writeArray2);
            realBuf = ByteBuffer.wrap(writeArray1.getBytes());
        }
        System.out.println("sncp.realBuf = " + realBuf.remaining());
        codec.decodeMessages(realBuf, new ByteArray());
        System.out.println("respResults.size = " + respResults.size());
        Assertions.assertEquals(2, respResults.size());
    }
}
