/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;
import org.redkale.convert.ConvertBytesHandler;
import static org.redkale.convert.pb.ProtobufWriter.CHILD_SIZE;
import org.redkale.util.ByteArray;
import org.redkale.util.ByteTuple;

/**
 *
 *
 * @author zhangjx
 */
public class ProtobufBytesWriter extends ProtobufWriter { // 存在child情况因此不能实现ByteTuple

    private static final int RESET_MAX_SIZE = DEFAULT_SIZE << 4;

    byte[] content;

    ProtobufBytesWriter child;

    private ProtobufBytesWriter parent;

    // 链表结构
    private ProtobufBytesWriter delegate;

    private ArrayDeque<ProtobufBytesWriter> pool;

    protected ProtobufBytesWriter(byte[] bs) {
        this.content = bs;
    }

    ProtobufBytesWriter(byte[] bs, int count) {
        this.content = bs;
        this.count = count;
    }

    public ProtobufBytesWriter() {
        this(DEFAULT_SIZE);
    }

    public ProtobufBytesWriter(int size) {
        this.content = new byte[Math.max(size, DEFAULT_SIZE)];
    }

    public ProtobufBytesWriter(ByteTuple tuple) {
        this.content = tuple.content();
        this.count = tuple.length();
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        if (this.delegate != null && this.pool != null && this.parent == null) {
            ProtobufBytesWriter s;
            ProtobufBytesWriter p = this.delegate;
            do {
                s = p;
                p = p.parent;
                offerPool(s);
            } while (p != this);
        }
        this.child = null;
        this.parent = null;
        this.delegate = null;
        if (this.content.length > RESET_MAX_SIZE) {
            this.content = new byte[DEFAULT_SIZE];
        }
        return true;
    }

    private void offerPool(ProtobufBytesWriter item) {
        if (this.pool != null && this.pool.size() < CHILD_SIZE) {
            item.recycle();
            this.pool.offer(item);
        }
    }

    @Override
    public ProtobufWriter pollChild() {
        Queue<ProtobufBytesWriter> queue = this.pool;
        if (queue == null) {
            // 必须要使用根节点的pool
            ProtobufBytesWriter root = null;
            if (this.parent != null) {
                ProtobufBytesWriter p = this;
                while ((p = p.parent) != null) {
                    root = p;
                }
            }
            if (root != null) {
                queue = root.pool;
                if (queue == null) {
                    root.pool = new ArrayDeque<>(CHILD_SIZE);
                    queue = root.pool;
                }
            }
        }
        if (queue == null) {
            this.pool = new ArrayDeque<>(CHILD_SIZE);
            queue = this.pool;
        }
        ProtobufBytesWriter result = queue.poll();
        if (result == null) {
            result = new ProtobufBytesWriter(new byte[DEFAULT_SIZE], 0);
        }
        if (delegate == null) {
            result.parent = this;
            this.child = result;
            delegate = result;
        } else {
            result.parent = delegate;
            delegate.child = result;
            delegate = result;
        }
        if (this.parent != null) {
            ProtobufBytesWriter p = this;
            while ((p = p.parent) != null) {
                p.delegate = result;
            }
        }
        result.configFieldFunc(result.parent);
        return result;
    }

    @Override
    public void offerChild(ProtobufWriter child) {
        ProtobufBytesWriter sub = (ProtobufBytesWriter) child;
        if (sub != null) {
            int len = sub.length();
            ProtobufBytesWriter next = sub;
            while ((next = next.child) != null) {
                len += next.length();
            }
            sub.parent.writeSelfLength(len);
        }
    }

    @Override
    protected final void writeSelfLength(int value) {
        ProtobufBytesWriter old = this.delegate;
        this.delegate = null;
        if (value < 128) {
            writeTo((byte) value);
        } else {
            writeUInt32(value);
        }
        this.delegate = old;
    }

    /**
     * 将本对象的内容引用复制给array
     *
     * @param array ByteArray
     */
    public void directTo(ByteArray array) {
        if (delegate == null) {
            array.directFrom(content, count);
        } else {
            byte[] data = toArray();
            array.directFrom(data, data.length);
        }
    }

    public void completed(ConvertBytesHandler handler, Consumer<ProtobufWriter> callback) {
        if (delegate == null) {
            handler.completed(content, 0, count, callback, this);
        } else {
            byte[] data = toArray();
            handler.completed(data, 0, data.length, callback, this);
        }
    }

    public byte[] toArray() {
        if (delegate == null) {
            byte[] copy = new byte[count];
            System.arraycopy(content, 0, copy, 0, count);
            return copy;
        } else {
            int total = count;
            ProtobufBytesWriter next = this;
            while ((next = next.child) != null) {
                total += next.length();
            }
            byte[] data = new byte[total];
            System.arraycopy(content, 0, data, 0, count);
            next = this;
            int pos = count;
            while ((next = next.child) != null) {
                System.arraycopy(next.content, 0, data, pos, next.length());
                pos += next.length();
            }
            return data;
        }
    }

    @Override
    public ByteArray toByteArray() {
        return new ByteArray(toArray());
    }

    // 类似writeTo(new byte[length])
    public void writePlaceholderTo(final int length) {
        expand(length);
        count += length;
    }

    @Override
    protected int expand(int len) {
        int newcount = count + len;
        if (newcount > content.length) {
            byte[] newdata = new byte[Math.max(content.length << 1, newcount)];
            System.arraycopy(content, 0, newdata, 0, count);
            this.content = newdata;
        }
        return 0;
    }

    @Override
    public void writeTo(final byte ch) {
        if (delegate == null) {
            expand(1);
            content[count++] = ch;
        } else {
            delegate.writeTo(ch);
        }
    }

    @Override
    public void writeTo(final byte[] chs, final int start, final int len) {
        if (delegate == null) {
            expand(len);
            System.arraycopy(chs, start, content, count, len);
            count += len;
        } else {
            delegate.writeTo(chs, start, len);
        }
    }

    @Override
    protected void writeUInt32(int value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES[value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES2[-value]);
            return;
        }
        if (delegate == null) {
            expand(5);
            int curr = this.count;
            byte[] data = this.content;
            while (true) {
                if ((value & ~0x7F) == 0) {
                    data[curr++] = (byte) value;
                    this.count = curr;
                    return;
                } else {
                    data[curr++] = (byte) ((value & 0x7F) | 0x80);
                    value >>>= 7;
                }
            }
        } else {
            delegate.writeUInt32(value);
        }
    }

    @Override
    protected void writeUInt64(long value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES[(int) value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES2[(int) -value]);
            return;
        }
        if (delegate == null) {
            expand(10);
            int curr = this.count;
            byte[] data = this.content;
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    data[curr++] = (byte) value;
                    this.count = curr;
                    return;
                } else {
                    data[curr++] = (byte) (((int) value & 0x7F) | 0x80);
                    value >>>= 7;
                }
            }
        } else {
            delegate.writeUInt64(value);
        }
    }
}
