/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.http;

import java.net.Socket;

/** @author zhangjx */
public class SocketMain {

    public static void main(String[] args) throws Throwable {
        Socket socket = new Socket("127.0.0.1", 6060);
        socket.getOutputStream()
                .write(
                        "GET /json1 HTTP/1.1\r\nAccpet: aaa\r\nConnection: keep-alive\r\n\r\nGET /json2 HTTP/1.1\r\nAccpet: a"
                                .getBytes());
        socket.getOutputStream().flush();
        Thread.sleep(1000);
        socket.getOutputStream().write("aa\r\nConnection: keep-alive\r\n\r".getBytes());
        socket.getOutputStream().flush();
        Thread.sleep(1000);
        socket.getOutputStream()
                .write("\nGET /json3 HTTP/1.1\r\nAccpet: aaa\r\nConnection: keep-alive\r\n\r".getBytes());
        socket.getOutputStream().flush();
        Thread.sleep(1000);
        socket.getOutputStream().write("\n".getBytes());
        socket.getOutputStream().flush();
        byte[] bs = new byte[10240];
        int rs = socket.getInputStream().read(bs);
        System.out.println(new String(bs, 0, rs));
        rs = socket.getInputStream().read(bs);
        System.out.println(new String(bs, 0, rs));
    }
}
