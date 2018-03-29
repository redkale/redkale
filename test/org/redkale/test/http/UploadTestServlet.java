/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.http;

import org.redkale.net.http.HttpServlet;
import org.redkale.net.http.MultiPart;
import org.redkale.net.http.HttpRequest;
import org.redkale.net.http.HttpResponse;
import java.io.*;

/**
 *
 * @author zhangjx
 */
//@WebServlet({"/uploadtest/form", "/uploadtest/send"})
public class UploadTestServlet extends HttpServlet {

    @Override
    public void execute(HttpRequest request, HttpResponse response) throws IOException {
        if (request.getRequestURI().contains("/uploadtest/send")) {
            send(request, response);
        } else {
            form(request, response);
        }
    }

    public void form(HttpRequest req, HttpResponse resp) throws IOException {
        resp.setContentType("text/html");
        resp.finish(
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/></head>"
                + "<div style='margin-top:150px;margin-left:400px;'><form action=\"/pipes/uploadtest/send\" method=\"post\" enctype=\"multipart/form-data\">"
                + "描述: <input name=\"desc1\"/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;文件1: <input type=\"file\" name=\"filepath1\"/><br/><br/>"
                + "描述: <input name=\"desc2\"/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;文件2: <input type=\"file\" name=\"filepath2\"/><br/><br/>"
                + "描述: <input name=\"desc3\"/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;文件3: <input type=\"file\" name=\"filepath3\"/><br/><br/>"
                + "描述: <input name=\"desc4\"/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br/><br/>"
                + "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<input type=\"submit\" value=\"Submit\"/></form></div>"
                + "</html>");
    }

    public void send(HttpRequest req, HttpResponse resp) throws IOException {
        for (MultiPart entry : req.multiParts()) {
            entry.skip();
            System.out.println(entry);
        }
        System.exit(0);
    }
}
