/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.net;

import java.nio.channels.CompletionHandler;
import org.redkale.convert.ConvertColumn;
import org.redkale.util.ByteTuple;

/**
 * pipelineWrite写入包
 *
 * @author zhangjx
 * @param <T> attachment类型
 * @since 2.8.0
 */
public class PipelinePacket<T> {

    @ConvertColumn(index = 1)
    protected byte[] tupleBytes;

    @ConvertColumn(index = 2)
    protected int tupleOffset;

    @ConvertColumn(index = 3)
    protected int tupleLength;

    @ConvertColumn(index = 4)
    protected CompletionHandler<Integer, T> handler;

    @ConvertColumn(index = 5)
    protected T attach;

    public PipelinePacket() {}

    public PipelinePacket(ByteTuple data, CompletionHandler<Integer, T> handler) {
        this(data, handler, null);
    }

    public PipelinePacket(ByteTuple data, CompletionHandler<Integer, T> handler, T attach) {
        this(data.content(), data.offset(), data.length(), handler, attach);
    }

    public PipelinePacket(byte[] tupleBytes, CompletionHandler<Integer, T> handler) {
        this(tupleBytes, 0, tupleBytes.length, handler, null);
    }

    public PipelinePacket(byte[] tupleBytes, CompletionHandler<Integer, T> handler, T attach) {
        this(tupleBytes, 0, tupleBytes.length, handler, attach);
    }

    public PipelinePacket(byte[] tupleBytes, int tupleOffset, int tupleLength, CompletionHandler<Integer, T> handler) {
        this(tupleBytes, tupleOffset, tupleLength, handler, null);
    }

    public PipelinePacket(
            byte[] tupleBytes, int tupleOffset, int tupleLength, CompletionHandler<Integer, T> handler, T attach) {
        this.tupleBytes = tupleBytes;
        this.tupleOffset = tupleOffset;
        this.tupleLength = tupleLength;
        this.handler = handler;
        this.attach = attach;
    }

    public byte[] getTupleBytes() {
        return tupleBytes;
    }

    public void setTupleBytes(byte[] tupleBytes) {
        this.tupleBytes = tupleBytes;
    }

    public int getTupleOffset() {
        return tupleOffset;
    }

    public void setTupleOffset(int tupleOffset) {
        this.tupleOffset = tupleOffset;
    }

    public int getTupleLength() {
        return tupleLength;
    }

    public void setTupleLength(int tupleLength) {
        this.tupleLength = tupleLength;
    }

    public CompletionHandler<Integer, T> getHandler() {
        return handler;
    }

    public void setHandler(CompletionHandler<Integer, T> handler) {
        this.handler = handler;
    }

    public T getAttach() {
        return attach;
    }

    public void setAttach(T attach) {
        this.attach = attach;
    }
}
