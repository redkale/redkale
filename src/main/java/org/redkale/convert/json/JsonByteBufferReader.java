/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.nio.ByteBuffer;
import java.nio.charset.UnmappableCharacterException;
import org.redkale.convert.*;
import static org.redkale.convert.Reader.*;

/**
 * 以ByteBuffer为数据载体的JsonReader <br>
 * 只支持UTF-8格式
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class JsonByteBufferReader extends JsonReader {

    private char currentChar;

    private ByteBuffer[] buffers;

    private int currentIndex = 0;

    private ByteBuffer currentBuffer;

    protected JsonByteBufferReader(ByteBuffer... buffers) {
        this.buffers = buffers;
        if (buffers != null && buffers.length > 0) {
            this.currentBuffer = buffers[currentIndex];
        }
    }

    @Override
    protected boolean recycle() {
        super.recycle(); // this.position 初始化值为-1
        this.currentChar = 0;
        this.buffers = null;
        this.currentIndex = 0;
        this.currentBuffer = null;
        return false;
    }

    protected byte nextByte() {
        if (this.currentBuffer.hasRemaining()) {
            this.position++;
            return this.currentBuffer.get();
        }
        for (; ; ) {
            this.currentBuffer = this.buffers[++this.currentIndex];
            if (this.currentBuffer.hasRemaining()) {
                this.position++;
                return this.currentBuffer.get();
            }
        }
    }

    /**
     * 读取下一个字符， 不跳过空白字符
     *
     * @return 有效字符或空白字符
     */
    @Override
    protected final char nextChar() {
        return nextChar(null);
    }

    protected final char nextChar(StringBuilder sb) {
        if (currentChar != 0) {
            char ch = currentChar;
            this.currentChar = 0;
            return ch;
        }
        if (this.currentBuffer != null) {
            int remain = this.currentBuffer.remaining();
            if (remain == 0 && this.currentIndex + 1 >= this.buffers.length) {
                return 0;
            }
        }
        byte b = nextByte();
        if (b >= 0) { // 1 byte, 7 bits: 0xxxxxxx
            return (char) b;
        } else if ((b >> 5) == -2 && (b & 0x1e) != 0) { // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
            return (char) (((b << 6) ^ nextByte()) ^ (((byte) 0xC0 << 6) ^ ((byte) 0x80)));
        } else if ((b >> 4) == -2) { // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
            return (char) ((b << 12)
                    ^ (nextByte() << 6)
                    ^ (nextByte() ^ (((byte) 0xE0 << 12) ^ ((byte) 0x80 << 6) ^ ((byte) 0x80))));
        } else if ((b >> 3) == -2) { // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
            int uc = ((b << 18)
                    ^ (nextByte() << 12)
                    ^ (nextByte() << 6)
                    ^ (nextByte() ^ (((byte) 0xF0 << 18) ^ ((byte) 0x80 << 12) ^ ((byte) 0x80 << 6) ^ ((byte) 0x80))));
            if (sb != null) {
                sb.append(Character.highSurrogate(uc));
            }
            return Character.lowSurrogate(uc);
        } else {
            throw new ConvertException(new UnmappableCharacterException(4));
        }
    }

    /**
     * 回退最后读取的字符
     *
     * @param ch 回退的字符
     */
    @Override
    protected final void backChar(char ch) {
        this.currentChar = ch;
    }

    /**
     * 判断下一个非空白字符是否为{
     *
     * @return SIGN_NOLENGTH 或 SIGN_NULL
     */
    @Override
    public final String readObjectB(final Class clazz) {
        this.fieldIndex = 0; // 必须要重置为0
        char ch = nextGoodChar(true);
        if (ch == '{') {
            return "";
        }
        if (ch == 'n' && nextChar() == 'u' && nextChar() == 'l' && nextChar() == 'l') {
            return null;
        }
        if (ch == 'N' && nextChar() == 'U' && nextChar() == 'L' && nextChar() == 'L') {
            return null;
        }
        int pos = this.position;
        StringBuilder sb = new StringBuilder();
        sb.append(ch);
        char one;
        try {
            while ((one = nextChar()) != 0) sb.append(one);
        } catch (Exception e) {
            // do nothing
        }
        throw new ConvertException(
                "a json object text must begin with '{' (position = " + pos + ") but '" + ch + "' in (" + sb + ")");
    }

    /**
     * 判断下一个非空白字符是否为[
     *
     * @param member DeMember
     * @param typevals byte[]
     * @param decoder Decodeable
     * @return SIGN_NOLENGTH 或 SIGN_NULL
     */
    @Override
    public final int readArrayB(DeMember member, byte[] typevals, Decodeable decoder) {
        char ch = nextGoodChar(true);
        if (ch == '[' || ch == '{') {
            return SIGN_NOLENGTH;
        }
        if (ch == 'n' && nextChar() == 'u' && nextChar() == 'l' && nextChar() == 'l') {
            return SIGN_NULL;
        }
        if (ch == 'N' && nextChar() == 'U' && nextChar() == 'L' && nextChar() == 'L') {
            return SIGN_NULL;
        }
        int pos = this.position;
        StringBuilder sb = new StringBuilder();
        sb.append(ch);
        char one;
        try {
            while ((one = nextChar()) != 0) sb.append(one);
        } catch (Exception e) {
            // do nothing
        }
        throw new ConvertException(
                "a json array text must begin with '[' (position = " + pos + ") but '" + ch + "' in (" + sb + ")");
    }

    /** 判断下一个非空白字符是否: */
    @Override
    public final void readBlank() {
        char ch = nextGoodChar(true);
        if (ch == ':') {
            return;
        }
        int pos = this.position;
        StringBuilder sb = new StringBuilder();
        sb.append(ch);
        char one;
        try {
            while ((one = nextChar()) != 0) sb.append(one);
        } catch (Exception e) {
            // do nothing
        }
        throw new ConvertException("expected a ':' but '" + ch + "'(position = " + pos + ") in (" + sb + ")");
    }

    /**
     * 读取小字符串
     *
     * @return String值
     */
    @Override
    public final String readSmallString() {
        return readString(true);
    }

    /**
     * 读取字符串， 必须是"或者'包围的字符串值
     *
     * @return String值
     */
    @Override
    public final String readString() {
        return readString(true);
    }

    @Override
    protected String readString(boolean flag) {
        char ch = nextGoodChar(true);
        if (ch == 0) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        if (ch == '"' || ch == '\'') {
            final char quote = ch;
            for (; ; ) {
                ch = nextChar(sb);
                if (ch == '\\') {
                    char c = nextChar(sb);
                    switch (c) {
                        case '"':
                        case '\'':
                        case '\\':
                        case '/':
                            sb.append(c);
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 'u':
                            sb.append((char) Integer.parseInt(
                                    new String(new char[] {nextChar(), nextChar(), nextChar(), nextChar()}), 16));
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        default:
                            throw new ConvertException("illegal escape(" + c + ") (position = " + this.position + ")");
                    }
                } else if (ch == quote || ch == 0) {
                    break;
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        } else {
            sb.append(ch);
            for (; ; ) {
                ch = nextChar(sb);
                if (ch == '\\') {
                    char c = nextChar(sb);
                    switch (c) {
                        case '"':
                        case '\'':
                        case '\\':
                        case '/':
                            sb.append(c);
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 'u':
                            sb.append((char) Integer.parseInt(
                                    new String(new char[] {nextChar(), nextChar(), nextChar(), nextChar()}), 16));
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        default:
                            throw new ConvertException("illegal escape(" + c + ") (position = " + this.position + ")");
                    }
                } else if (ch == ',' || ch == ']' || ch == '}' || ch <= ' ' || (flag && ch == ':')) { //  ch <= ' ' 包含 0
                    backChar(ch);
                    break;
                } else {
                    sb.append(ch);
                }
            }
            String rs = sb.toString();
            return "null".equalsIgnoreCase(rs) ? null : rs;
        }
    }

    @Override
    public DeMember readFieldName(final DeMemberInfo memberInfo) {
        char ch = nextGoodChar(true);
        if (ch == 0) {
            return null;
        }
        DeMemberNode node = memberInfo.getMemberNode();
        StringBuilder sb = new StringBuilder();
        if (ch == '"' || ch == '\'') {
            final char quote = ch;
            for (; ; ) {
                ch = nextChar(sb);
                if (ch == quote || ch == 0) {
                    break;
                } else {
                    node = node == null ? null : node.getNode(ch);
                }
            }
            return node == null ? null : node.getValue();
        } else {
            node = node == null ? null : node.getNode(ch);
            for (; ; ) {
                ch = nextChar(sb);
                if (ch == ',' || ch == ']' || ch == '}' || ch <= ' ' || ch == ':') { //  ch <= ' ' 包含 0
                    backChar(ch);
                    break;
                } else {
                    node = node == null ? null : node.getNode(ch);
                }
            }
            return node == null ? null : node.getValue();
        }
    }
}
