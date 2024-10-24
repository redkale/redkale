/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.nio.ByteBuffer;
import java.nio.charset.UnmappableCharacterException;
import org.redkale.convert.*;
import org.redkale.util.ByteTreeNode;

/**
 * 以ByteBuffer为数据载体的JsonReader <br>
 * 只支持UTF-8格式
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class JsonByteBufferReader extends JsonReader {

    private char cacheChar;

    private char currentChar;

    private ByteBuffer[] buffers;

    private ByteBuffer currentBuffer;

    private int currBufIndex = 0;

    protected JsonByteBufferReader(ByteBuffer... buffers) {
        this.buffers = buffers;
        if (buffers != null && buffers.length > 0) {
            this.currentBuffer = buffers[currBufIndex];
        }
    }

    @Override
    protected boolean recycle() {
        super.recycle(); // this.position 初始化值为-1
        this.cacheChar = 0;
        this.buffers = null;
        this.currBufIndex = 0;
        this.currentBuffer = null;
        return false;
    }

    protected byte nextByte() {
        if (this.currentBuffer.hasRemaining()) {
            this.position++;
            return this.currentBuffer.get();
        }
        for (; ; ) {
            this.currentBuffer = this.buffers[++this.currBufIndex];
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

    protected final char currChar() {
        return currentChar;
    }

    protected final char nextChar(CharArray sb) {
        if (cacheChar != 0) {
            char ch = cacheChar;
            this.cacheChar = 0;
            this.currentChar = ch;
            return ch;
        }

        byte b = nextByte();
        if (b >= 0) { // 1 byte, 7 bits: 0xxxxxxx
            char ch = (char) b;
            this.currentChar = ch;
            return ch;
        } else if ((b >> 5) == -2 && (b & 0x1e) != 0) { // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
            char ch = (char) (((b << 6) ^ nextByte()) ^ (((byte) 0xC0 << 6) ^ ((byte) 0x80)));
            this.currentChar = ch;
            return ch;
        } else if ((b >> 4) == -2) { // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
            char ch = (char) ((b << 12)
                    ^ (nextByte() << 6)
                    ^ (nextByte() ^ (((byte) 0xE0 << 12) ^ ((byte) 0x80 << 6) ^ ((byte) 0x80))));
            this.currentChar = ch;
            return ch;
        } else if ((b >> 3) == -2) { // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
            int uc = ((b << 18)
                    ^ (nextByte() << 12)
                    ^ (nextByte() << 6)
                    ^ (nextByte() ^ (((byte) 0xF0 << 18) ^ ((byte) 0x80 << 12) ^ ((byte) 0x80 << 6) ^ ((byte) 0x80))));
            if (sb != null) {
                sb.append(Character.highSurrogate(uc));
            }
            char ch = Character.lowSurrogate(uc);
            this.currentChar = ch;
            return ch;
        } else {
            throw new ConvertException(new UnmappableCharacterException(4));
        }
    }

    /**
     * 跳过空白字符、单行或多行注释， 返回一个非空白字符
     *
     * @param allowComment 是否容许含注释
     * @return 有效字符
     */
    @Override
    protected char nextGoodChar(boolean allowComment) {
        char c;
        for (; ; ) {
            c = nextChar();
            if (c == 0) {
                return c; // 0 表示buffer结尾了
            }
            if (c > ' ') {
                if (allowComment && c == '/') { // 支持单行和多行注释
                    char n = nextChar();
                    if (n == '/') {
                        for (; ; ) {
                            if (nextChar() == '\n') {
                                break;
                            }
                        }
                        return nextGoodChar(allowComment);
                    } else if (n == '*') {
                        char nc;
                        char lc = 0;
                        for (; ; ) {
                            nc = nextChar();
                            if (nc == '/' && lc == '*') {
                                break;
                            }
                            lc = nc;
                        }
                        return nextGoodChar(allowComment);
                    } else {
                        throw new ConvertException("illegal escape(" + n + ") (position = " + this.position + ")");
                    }
                }
                return c;
            }
        }
    }

    /**
     * 回退最后读取的字符
     *
     * @param ch 回退的字符
     */
    @Override
    protected final void backChar(char ch) {
        this.cacheChar = ch;
    }

    /**
     * 读取对象，返回false表示对象为null
     *
     * @param decoder Decodeable
     * @return 是否存在对象
     */
    @Override
    public final boolean readObjectB(Decodeable decoder) {
        char ch = nextGoodChar(true);
        if (ch == '{') {
            return true;
        }
        if (ch == 'n' && nextChar() == 'u' && nextChar() == 'l' && nextChar() == 'l') {
            return false;
        }
        if (ch == 'N' && nextChar() == 'U' && nextChar() == 'L' && nextChar() == 'L') {
            return false;
        }
        throw new ConvertException("a json object must begin with '{' (position = " + position + ") but '" + ch + "'");
    }

    /**
     * 读取数组，返回false表示数组为null
     *
     * @param componentDecoder Decodeable
     * @return 是否存在对象
     */
    @Override
    public final boolean readArrayB(Decodeable componentDecoder) {
        char ch = nextGoodChar(true);
        if (ch == '[' || ch == '{') {
            return true;
        }
        if (ch == 'n' && nextChar() == 'u' && nextChar() == 'l' && nextChar() == 'l') {
            return false;
        }
        if (ch == 'N' && nextChar() == 'U' && nextChar() == 'L' && nextChar() == 'L') {
            return false;
        }
        int pos = this.position;
        throw new ConvertException("a json array must begin with '[' (position = " + pos + ") but '" + ch + "'");
    }

    /** 判断下一个非空白字符是否: */
    @Override
    public final void readColon() {
        char ch = nextGoodChar(true);
        if (ch == ':') {
            return;
        }
        throw new ConvertException("expected a ':' but '" + ch + "'(position = " + this.position + ")");
    }

    /**
     * 读取一个int值
     *
     * @return int值
     */
    @Override
    public int readInt() {
        char firstchar = nextGoodChar(true);
        char quote = 0;
        if (firstchar == '"' || firstchar == '\'') {
            quote = firstchar;
            firstchar = nextGoodChar(false);
            if (firstchar == quote) {
                return 0;
            }
        }
        int value = 0;
        final boolean negative = firstchar == '-';
        if (negative) { // 负数
            firstchar = nextChar();
            if (firstchar != 'N' && firstchar != 'I') {
                if (firstchar < '0' || firstchar > '9') {
                    throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
                }
                value = digits[firstchar];
            }
        } else { // 正数
            if (firstchar == '+') {
                firstchar = nextChar(); // 兼容+开头的
            }
            if (firstchar != 'N' && firstchar != 'I') {
                if (firstchar < '0' || firstchar > '9') {
                    throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
                }
                value = digits[firstchar];
            }
        }
        if (firstchar == 'N') {
            if (negative) {
                throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
            }
            if (nextChar() != 'a' || nextChar() != 'N') {
                throw new ConvertException("illegal escape(" + currChar() + ") (position = " + position + ")");
            }
            if (quote > 0 && nextChar() != quote) {
                throw new ConvertException("illegal escape(" + currChar() + ") (position = " + position + ")");
            }
            return 0; // NaN 返回0;
        } else if (firstchar == 'I') { // Infinity
            if (nextChar() != 'n'
                    || nextChar() != 'f'
                    || nextChar() != 'i'
                    || nextChar() != 'n'
                    || nextChar() != 'i'
                    || nextChar() != 't'
                    || nextChar() != 'y') {
                throw new ConvertException("illegal escape(" + currChar() + ") (position = " + position + ")");
            }
            if (quote > 0 && nextChar() != quote) {
                throw new ConvertException("illegal escape(" + currChar() + ") (position = " + position + ")");
            }
            return negative ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        boolean hex = false;
        boolean dot = false;
        for (; ; ) {
            char ch = nextChar();
            if (ch == 0) {
                break;
            }
            if (ch >= '0' && ch <= '9') {
                if (dot) { // 兼容 123.456
                    continue;
                }
                value = (hex ? (value << 4) : ((value << 3) + (value << 1))) + digits[ch];
            } else if (ch == quote) {
                break;
            } else if (ch == 'x' || ch == 'X') {
                if (value != 0) {
                    throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
                }
                hex = true;
            } else if (ch >= 'a' && ch <= 'f') {
                if (!hex) {
                    throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
                }
                if (dot) {
                    continue;
                }
                value = (value << 4) + digits[ch];
            } else if (ch >= 'A' && ch <= 'F') {
                if (!hex) {
                    throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
                }
                if (dot) {
                    continue;
                }
                value = (value << 4) + digits[ch];
            } else if (quote > 0 && ch <= ' ') { // 兼容 "123 "
                // do nothing
            } else if (ch == '.') {
                dot = true;
            } else if (ch == ',' || ch == '}' || ch == ']' || ch <= ' ' || ch == ':') {
                backChar(ch);
                break;
            } else {
                throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
            }
        }
        return negative ? -value : value;
    }

    /**
     * 读取一个long值
     *
     * @return long值
     */
    @Override
    public long readLong() {
        char firstchar = nextGoodChar(true);
        char quote = 0;
        if (firstchar == '"' || firstchar == '\'') {
            quote = firstchar;
            firstchar = nextGoodChar(false);
            if (firstchar == firstchar) {
                return 0L;
            }
        }
        long value = 0;
        final boolean negative = firstchar == '-';
        if (negative) { // 负数
            firstchar = nextChar();
            if (firstchar != 'N' && firstchar != 'I') {
                if (firstchar < '0' || firstchar > '9') {
                    throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
                }
                value = digits[firstchar];
            }
        } else { // 正数
            if (firstchar == '+') {
                firstchar = nextChar(); // 兼容+开头的
            }
            if (firstchar != 'N' && firstchar != 'I') {
                if (firstchar < '0' || firstchar > '9') {
                    throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
                }
                value = digits[firstchar];
            }
        }
        if (firstchar == 'N') {
            if (negative) {
                throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
            }
            if (nextChar() != 'a' || nextChar() != 'N') {
                throw new ConvertException("illegal escape(" + currChar() + ") (position = " + position + ")");
            }
            if (quote > 0 && nextChar() != quote) {
                throw new ConvertException("illegal escape(" + currChar() + ") (position = " + position + ")");
            }
            return 0; // NaN 返回0;
        } else if (firstchar == 'I') { // Infinity
            if (nextChar() != 'n'
                    || nextChar() != 'f'
                    || nextChar() != 'i'
                    || nextChar() != 'n'
                    || nextChar() != 'i'
                    || nextChar() != 't'
                    || nextChar() != 'y') {
                throw new ConvertException("illegal escape(" + currChar() + ") (position = " + position + ")");
            }
            if (quote > 0 && nextChar() != quote) {
                throw new ConvertException("illegal escape(" + currChar() + ") (position = " + position + ")");
            }
            return negative ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        boolean hex = false;
        boolean dot = false;
        for (; ; ) {
            char ch = nextChar();
            if (ch == 0) {
                break;
            }
            if (ch >= '0' && ch <= '9') {
                if (dot) { // 兼容 123.456
                    continue;
                }
                value = (hex ? (value << 4) : ((value << 3) + (value << 1))) + digits[ch];
            } else if (ch == quote) {
                break;
            } else if (ch == 'x' || ch == 'X') {
                if (value != 0) {
                    throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
                }
                hex = true;
            } else if (ch >= 'a' && ch <= 'f') {
                if (!hex) {
                    throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
                }
                if (dot) {
                    continue;
                }
                value = (value << 4) + digits[ch];
            } else if (ch >= 'A' && ch <= 'F') {
                if (!hex) {
                    throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
                }
                if (dot) {
                    continue;
                }
                value = (value << 4) + digits[ch];
            } else if (quote > 0 && ch <= ' ') { // 兼容 "123 "
                // do nothing
            } else if (ch == '.') {
                dot = true;
            } else if (ch == ',' || ch == '}' || ch == ']' || ch <= ' ' || ch == ':') {
                backChar(ch);
                break;
            } else {
                throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
            }
        }
        return negative ? -value : value;
    }

    /**
     * 读取小字符串
     *
     * @return String值
     */
    @Override
    public final String readStandardString() {
        return readString(true);
    }

    @Override
    protected String readString(boolean flag) {
        char ch = nextGoodChar(true);
        if (ch == 0) {
            return null;
        }
        final CharArray tmp = array();
        if (ch == '"' || ch == '\'') {
            final char quote = ch;
            for (; ; ) {
                ch = nextChar(tmp);
                if (ch == '\\') {
                    char c = nextChar(tmp);
                    switch (c) {
                        case '"':
                        case '\'':
                        case '\\':
                        case '/':
                            tmp.append(c);
                            break;
                        case 'n':
                            tmp.append('\n');
                            break;
                        case 'r':
                            tmp.append('\r');
                            break;
                        case 'u':
                            tmp.append((char) Integer.parseInt(
                                    new String(new char[] {nextChar(), nextChar(), nextChar(), nextChar()}), 16));
                            break;
                        case 't':
                            tmp.append('\t');
                            break;
                        case 'b':
                            tmp.append('\b');
                            break;
                        case 'f':
                            tmp.append('\f');
                            break;
                        default:
                            throw new ConvertException("illegal escape(" + c + ") (position = " + this.position + ")");
                    }
                } else if (ch == quote || ch == 0) {
                    break;
                } else {
                    tmp.append(ch);
                }
            }
            return tmp.toStringThenClear();
        } else {
            tmp.append(ch);
            for (; ; ) {
                ch = nextChar(tmp);
                if (ch == '\\') {
                    char c = nextChar(tmp);
                    switch (c) {
                        case '"':
                        case '\'':
                        case '\\':
                        case '/':
                            tmp.append(c);
                            break;
                        case 'n':
                            tmp.append('\n');
                            break;
                        case 'r':
                            tmp.append('\r');
                            break;
                        case 'u':
                            int hex = (Character.digit(nextChar(), 16) << 12)
                                    + (Character.digit(nextChar(), 16) << 8)
                                    + (Character.digit(nextChar(), 16) << 4)
                                    + Character.digit(nextChar(), 16);
                            tmp.append((char) hex);
                            break;
                        case 't':
                            tmp.append('\t');
                            break;
                        case 'b':
                            tmp.append('\b');
                            break;
                        case 'f':
                            tmp.append('\f');
                            break;
                        default:
                            throw new ConvertException("illegal escape(" + c + ") (position = " + this.position + ")");
                    }
                } else if (ch == ',' || ch == ']' || ch == '}' || ch <= ' ' || (flag && ch == ':')) { //  ch <= ' ' 包含 0
                    backChar(ch);
                    break;
                } else {
                    tmp.append(ch);
                }
            }
            String rs = tmp.toStringThenClear();
            return "null".equalsIgnoreCase(rs) ? null : rs;
        }
    }

    @Override
    public DeMember readField(final DeMemberInfo memberInfo) {
        char ch = nextGoodChar(true);
        if (ch == 0) {
            return null;
        }
        ByteTreeNode<DeMember> node = memberInfo.getMemberNode();
        CharArray tmp = array();
        if (ch == '"' || ch == '\'') {
            final char quote = ch;
            for (; ; ) {
                ch = nextChar(tmp);
                if (ch == quote || ch == 0) {
                    break;
                } else {
                    node = node == null ? null : node.getNode(ch);
                }
            }
            tmp.clear();
            return node == null ? null : node.getValue();
        } else {
            node = node == null ? null : node.getNode(ch);
            for (; ; ) {
                ch = nextChar(tmp);
                if (ch == ',' || ch == ']' || ch == '}' || ch <= ' ' || ch == ':') { //  ch <= ' ' 包含 0
                    backChar(ch);
                    break;
                } else {
                    node = node == null ? null : node.getNode(ch);
                }
            }
            tmp.clear();
            return node == null ? null : node.getValue();
        }
    }
}
