/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

/**
 * 在appliation.xml中的HTTP类型的server节点加上forwardproxy="true"表示该HttpServer支持正向代理
 *
 * @author zhangjx
 */
public final class HttpProxyServlet extends HttpServlet {

    @Override
    public void execute(HttpRequest request, HttpResponse response) throws IOException {
        response.skipHeader();
        if ("CONNECT".equalsIgnoreCase(request.getMethod())) {
            connect(request, response);
            return;
        }
        String url = request.getRequestURI();
        url = url.substring(url.indexOf("://") + 3);
        url = url.substring(url.indexOf('/'));
        final ByteBuffer buffer = response.getContext().pollBuffer();
        buffer.put((request.getMethod() + " " + url + " HTTP/1.1\r\n").getBytes());
        for (AnyValue.Entry<String> en : request.header.getStringEntrys()) {
            if (!en.name.startsWith("Proxy-")) {
                buffer.put((en.name + ": " + en.getValue() + "\r\n").getBytes());
            }
        }
        if (request.getHost() != null) {
            buffer.put(("Host: " + request.getHost() + "\r\n").getBytes());
        }
        if (request.getContentType() != null) {
            buffer.put(("Content-Type: " + request.getContentType() + "\r\n").getBytes());
        }
        if (request.getContentLength() > 0) {
            buffer.put(("Content-Length: " + request.getContentLength() + "\r\n").getBytes());
        }
        buffer.put(HttpResponse.LINE);
        buffer.flip();
        final AsyncConnection remote = AsyncConnection.create("TCP", request.getHostSocketAddress(), 6, 6);
        remote.write(buffer, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                if (buffer.hasRemaining()) {
                    remote.write(buffer, attachment, this);
                    return;
                }
                response.getContext().offerBuffer(buffer);
                new ProxyCompletionHandler(remote, request, response).completed(0, null);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                response.getContext().offerBuffer(buffer);
                response.finish(true);
                try {
                    remote.close();
                } catch (IOException ex) {
                }
            }
        });
    }

    private void connect(HttpRequest request, HttpResponse response) throws IOException {
        final InetSocketAddress remoteAddress = HttpRequest.parseSocketAddress(request.getRequestURI());
        final AsyncConnection remote = remoteAddress.getPort() == 443
                ? AsyncConnection.create(Utility.createDefaultSSLSocket(remoteAddress)) : AsyncConnection.create("TCP", remoteAddress, 6, 6);
        final ByteBuffer buffer0 = response.getContext().pollBuffer();
        buffer0.put("HTTP/1.1 200 Connection established\r\nConnection: close\r\n\r\n".getBytes());
        buffer0.flip();
        response.sendBody(buffer0, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                new ProxyCompletionHandler(remote, request, response).completed(0, null);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                response.finish(true);
                try {
                    remote.close();
                } catch (IOException ex) {
                }
            }
        });

    }

    private static class ProxyCompletionHandler implements CompletionHandler<Integer, Void> {

        private AsyncConnection remote;

        private HttpRequest request;

        private HttpResponse response;

        public ProxyCompletionHandler(AsyncConnection remote, HttpRequest request, HttpResponse response) {
            this.remote = remote;
            this.request = request;
            this.response = response;
        }

        @Override
        public void completed(Integer result0, Void v0) {
            final ByteBuffer rbuffer = request.getContext().pollBuffer();
            remote.read(rbuffer, null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    rbuffer.flip();
                    CompletionHandler parent = this;
                    response.sendBody(rbuffer.duplicate().asReadOnlyBuffer(), null, new CompletionHandler<Integer, Void>() {

                        @Override
                        public void completed(Integer result, Void attachment) {
                            rbuffer.clear();
                            remote.read(rbuffer, attachment, parent);
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            parent.failed(exc, attachment);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    response.getContext().offerBuffer(rbuffer);
                    response.finish(true);
                    try {
                        remote.close();
                    } catch (IOException ex) {
                    }
                }
            });

            final ByteBuffer qbuffer = request.getContext().pollBuffer();
            request.getChannel().read(qbuffer, null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    qbuffer.flip();
                    CompletionHandler parent = this;
                    remote.write(qbuffer, null, new CompletionHandler<Integer, Void>() {

                        @Override
                        public void completed(Integer result, Void attachment) {
                            qbuffer.clear();
                            request.getChannel().read(qbuffer, null, parent);
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            parent.failed(exc, attachment);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    response.getContext().offerBuffer(qbuffer);
                    response.finish(true);
                    try {
                        remote.close();
                    } catch (IOException ex) {
                    }
                }
            });
        }

        @Override
        public void failed(Throwable exc, Void v) {
            response.finish(true);
            try {
                remote.close();
            } catch (IOException ex) {
            }
        }
    }

}
