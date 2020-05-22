/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.nio;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public abstract class NioEventLoop extends AbstractLoop {

    protected final Selector selector;

    public NioEventLoop(String name) {
        super(name);
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processKey(SelectionKey key) {
        if (key == null || !key.isValid()) return;
        if (key.isAcceptable()) {
            try {
                acceptOP(key);
            } catch (Throwable e) {
                failedOP(key, e);
            }
        } else if (key.isConnectable()) {
            try {
                connectOP(key);
            } catch (Throwable e) {
                failedOP(key, e);
            }
        } else if (key.isReadable()) {
            try {
                readOP(key);
            } catch (Throwable e) {
                failedOP(key, e);
            }

        } else if (key.isWritable()) {
            try {
                writeOP(key);
            } catch (Throwable e) {
                failedOP(key, e);
            }
        }
    }

    @Override
    protected final void doLoop() {
        try {
            doLoopProcessing();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            selector.select(getSelectorTimeout());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            synchronized (selectedKeys) {
                Iterator<?> iter = selectedKeys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = (SelectionKey) iter.next();
                    iter.remove();
                    processKey(key);
                }
            }
        } catch (ClosedSelectorException e) {
            // do nothing
        }
    }

    protected long getSelectorTimeout() {
        return 10;
    }

    protected abstract void doLoopProcessing();

    protected void acceptOP(SelectionKey key) {
        throw new RuntimeException("Accept operation is not implemented!");
    }

    protected void connectOP(SelectionKey key) throws IOException {
        throw new RuntimeException("Connect operation is not implemented!");
    }

    protected void readOP(SelectionKey key) throws IOException {
        throw new RuntimeException("Accept operation is not implemented!");
    }

    protected void writeOP(SelectionKey key) throws IOException {
        throw new RuntimeException("Accept operation is not implemented!");
    }

    protected void failedOP(SelectionKey key, Throwable e) {
        // ignore the errors by default
    }

}
