/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.websocket;

import java.io.ByteArrayOutputStream;
import java.net.Socket;

/**
 *
 * @author zhangjx
 */
public class Flash843 {

    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("113.105.88.229", 843);
        socket.getOutputStream().write("<policy-file-request/>".getBytes());
        socket.getOutputStream().flush();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int pos;
        while ((pos = socket.getInputStream().read(bytes)) != -1) {
            out.write(bytes, 0, pos);
        }
        System.out.println(out.toString());
    }
}
