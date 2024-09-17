/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.redkale.convert.*;
import static org.redkale.convert.Reader.*;
import org.redkale.convert.Reader.ValueType;
import org.redkale.util.Utility;

/**
 * JSON数据源
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class JsonReader extends Reader {

    protected int position = -1;

    private char[] text;

    private int limit;

    private CharArray array;

    public JsonReader() {}

    public JsonReader(String json) {
        setText(Utility.charArray(json));
    }

    public JsonReader(char[] text) {
        setText(text, 0, text.length);
    }

    public JsonReader(char[] text, int start, int len) {
        setText(text, start, len);
    }

    public final JsonReader setText(String text) {
        return setText(Utility.charArray(text));
    }

    public final JsonReader setText(char[] text) {
        return setText(text, 0, text.length);
    }

    public final JsonReader setText(char[] text, int start, int len) {
        this.text = text;
        this.position = start - 1;
        this.limit = start + len - 1;
        return this;
    }

    @Override
    public void prepare(byte[] bytes) {
        if (bytes != null) {
            setText(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    protected boolean recycle() {
        this.position = -1;
        this.limit = -1;
        this.text = null;
        if (this.array != null) {
            if (this.array.content.length > CharArray.DEFAULT_SIZE * 200) {
                this.array = null;
            } else {
                this.array.clear();
            }
        }
        return true;
    }

    protected CharArray array() {
        if (array == null) {
            array = new CharArray();
        }
        return array.clear();
    }

    public void close() {
        this.recycle();
    }

    /**
     * 找到指定的属性值 例如: {id : 1, data : { name : 'a', items : [1,2,3]}} seek('data.items') 直接跳转到 [1,2,3];
     *
     * @param key 指定的属性名
     */
    public final void seek(String key) {
        if (key == null || key.length() < 1) {
            return;
        }
        final String[] keys = key.split("\\.");
        nextGoodChar(true); // 读掉 { [
        for (String key1 : keys) {
            while (this.hasNext()) {
                String field = this.readSmallString();
                readBlank();
                if (key1.equals(field)) {
                    break;
                }
                skipValue();
            }
        }
    }

    /** 跳过属性的值 */
    @Override
    public final void skipValue() {
        final char ch = nextGoodChar(true);
        switch (ch) {
            case '"':
            case '\'':
                backChar(ch);
                readString();
                break;
            case '{':
                while (hasNext()) {
                    this.readSmallString(); // 读掉field
                    this.readBlank();
                    this.skipValue();
                }
                break;
            case '[':
                while (hasNext()) {
                    this.skipValue();
                }
                break;
            default:
                char c;
                for (; ; ) {
                    c = nextChar();
                    if (c <= ' ') {
                        return;
                    }
                    if (c == '}' || c == ']' || c == ',' || c == ':') {
                        backChar(c);
                        return;
                    }
                }
        }
    }

    /**
     * 读取下一个字符， 不跳过空白字符
     *
     * @return 空白字符或有效字符
     */
    protected char nextChar() {
        int p = ++this.position;
        if (p >= text.length) {
            return 0;
        }
        return this.text[p];
    }

    /**
     * 跳过空白字符、单行或多行注释， 返回一个非空白字符
     *
     * @param allowComment 是否容许含注释
     * @return 有效字符
     */
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
                        throw new ConvertException("illegal escape(" + n + ") (position = " + this.position + ") in '"
                                + new String(text) + "'");
                    }
                }
                return c;
            }
        }
    }

    /**
     * 回退最后读取的字符
     *
     * @param ch 后退的字符
     */
    protected void backChar(char ch) {
        this.position--;
    }

    /**
     * 是否{开头的对象字符
     *
     * @return 是否对象字符
     */
    public boolean isNextObject() {
        char ch = nextGoodChar(true);
        backChar(ch);
        return ch == '{';
    }

    /**
     * 是否[开头的数组字符
     *
     * @return 是否数组字符
     */
    public boolean isNextArray() {
        char ch = nextGoodChar(true);
        backChar(ch);
        return ch == '[';
    }

    @Override
    public final ValueType readType() {
        char ch = nextGoodChar(true);
        if (ch == '{') {
            backChar(ch);
            return ValueType.MAP;
        }
        if (ch == '[') {
            backChar(ch);
            return ValueType.ARRAY;
        }
        backChar(ch);
        return ValueType.STRING;
    }

    /**
     * 判断下一个非空白字符是否为{
     *
     * @param clazz 类名
     * @return 返回 null 表示对象为null， 返回空字符串表示当前class与返回的class一致，返回非空字符串表示class是当前class的子类。
     */
    @Override
    public String readObjectB(final Class clazz) {
        this.fieldIndex = 0; // 必须要重置为0
        if (this.text.length == 0) {
            return null;
        }
        char ch = nextGoodChar(true);
        if (ch == '{') {
            return "";
        }
        if (ch == 'n' && text[++position] == 'u' && text[++position] == 'l' && text[++position] == 'l') {
            return null;
        }
        if (ch == 'N' && text[++position] == 'U' && text[++position] == 'L' && text[++position] == 'L') {
            return null;
        }
        throw new ConvertException("a json object text must begin with '{' (position = " + position + ") but '" + ch
                + "' in " + new String(this.text));
    }

    @Override
    public final void readObjectE(final Class clazz) {}

    /**
     * 判断下一个非空白字符是否为{
     *
     * @param member DeMember
     * @param typevals byte[]
     * @param keyDecoder Decodeable
     * @param valuedecoder Decodeable
     * @return SIGN_NOLENGTH 或 SIGN_NULL
     */
    @Override
    public final int readMapB(DeMember member, byte[] typevals, Decodeable keyDecoder, Decodeable valuedecoder) {
        return readArrayB(member, typevals, keyDecoder);
    }

    @Override
    public final void readMapE() {}

    /**
     * 判断下一个非空白字符是否为[
     *
     * @param member DeMember
     * @param typevals byte[]
     * @param componentDecoder Decodeable
     * @return SIGN_NOLENGTH 或 SIGN_NULL
     */
    @Override
    public int readArrayB(DeMember member, byte[] typevals, Decodeable componentDecoder) {
        if (this.text.length == 0) {
            return SIGN_NULL;
        }
        char ch = nextGoodChar(true);
        if (ch == '[') {
            return SIGN_NOLENGTH;
        }
        if (ch == '{') {
            return SIGN_NOLENGTH;
        }
        if (ch == 'n' && text[++position] == 'u' && text[++position] == 'l' && text[++position] == 'l') {
            return SIGN_NULL;
        }
        if (ch == 'N' && text[++position] == 'U' && text[++position] == 'L' && text[++position] == 'L') {
            return SIGN_NULL;
        }
        throw new ConvertException("a json array text must begin with '[' (position = " + position + ") but '" + ch
                + "' in " + new String(this.text));
    }

    @Override
    public final void readArrayE() {}

    /** 判断下一个非空白字符是否: */
    @Override
    public void readBlank() {
        char ch = nextGoodChar(true);
        if (ch == ':') {
            return;
        }
        throw new ConvertException("'" + new String(text) + "'expected a ':' but '" + ch + "'(position = " + position
                + ") in (" + new String(this.text) + ")");
    }

    @Override
    public int position() {
        return this.position;
    }

    @Override
    public int readMemberContentLength(DeMember member, Decodeable decoder) {
        return -1;
    }

    /**
     * 判断对象是否存在下一个属性或者数组是否存在下一个元素
     *
     * @param startPosition 起始位置
     * @param contentLength 内容大小， 不确定的传-1
     * @return 是否存在
     */
    @Override
    public boolean hasNext(int startPosition, int contentLength) {
        char ch = nextGoodChar(true);
        if (ch == ',') {
            char nt = nextGoodChar(true);
            if (nt == '}' || nt == ']') {
                return false;
            }
            backChar(nt);
            return true;
        }
        if (ch == '}' || ch == ']') {
            return false;
        }
        backChar(ch); // { [ 交由 readObjectB 或 readMapB 或 readArrayB 读取
        return true;
    }

    @Override
    public final String readClassName() {
        return null;
    }

    /**
     * 读取一个int值
     *
     * @return int值
     */
    @Override
    public int readInt() {
        char firstchar = nextGoodChar(true);
        boolean quote = false;
        if (firstchar == '"' || firstchar == '\'') {
            quote = true;
            firstchar = nextGoodChar(false);
            if (firstchar == '"' || firstchar == '\'') {
                return 0;
            }
        }
        int value = 0;
        final boolean negative = firstchar == '-';
        if (!negative) {
            if (firstchar == '+') {
                firstchar = nextChar(); // 兼容+开头的
            }
            if (firstchar < '0' || firstchar > '9') {
                throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
            }
            value = digits[firstchar];
        }
        if (firstchar == 'N') {
            if (negative) {
                throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
            }
            char c = nextChar();
            if (c != 'a') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'N') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            if (quote) {
                c = nextChar();
                if (c != '"' && c != '\'') {
                    throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
                }
            }
            return 0; // NaN 返回0;
        } else if (firstchar == 'I') { // Infinity
            char c = nextChar();
            if (c != 'n') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'f') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'i') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'n') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'i') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 't') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'y') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            if (quote) {
                c = nextChar();
                if (c != '"' && c != '\'') {
                    throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
                }
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
                if (dot) {
                    continue;
                }
                value = (hex ? (value << 4) : ((value << 3) + (value << 1))) + digits[ch];
            } else if (ch == '"' || ch == '\'') {
                if (quote) {
                    break;
                }
                throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
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
            } else if (quote && ch <= ' ') {
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
        boolean quote = false;
        if (firstchar == '"' || firstchar == '\'') {
            quote = true;
            firstchar = nextGoodChar(false);
            if (firstchar == '"' || firstchar == '\'') {
                return 0L;
            }
        }
        long value = 0;
        final boolean negative = firstchar == '-';
        if (!negative) {
            if (firstchar == '+') {
                firstchar = nextChar(); // 兼容+开头的
            }
            if (firstchar < '0' || firstchar > '9') {
                throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
            }
            value = digits[firstchar];
        }
        if (firstchar == 'N') {
            if (negative) {
                throw new ConvertException("illegal escape(" + firstchar + ") (position = " + position + ")");
            }
            char c = nextChar();
            if (c != 'a') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'N') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            if (quote) {
                c = nextChar();
                if (c != '"' && c != '\'') {
                    throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
                }
            }
            return 0L; // NaN 返回0;
        } else if (firstchar == 'I') { // Infinity
            char c = nextChar();
            if (c != 'n') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'f') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'i') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'n') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'i') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 't') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            c = nextChar();
            if (c != 'y') {
                throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
            }
            if (quote) {
                c = nextChar();
                if (c != '"' && c != '\'') {
                    throw new ConvertException("illegal escape(" + c + ") (position = " + position + ")");
                }
            }
            return negative ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        boolean hex = false;
        boolean dot = false;
        for (; ; ) {
            char ch = nextChar();
            if (ch == 0) {
                break;
            }
            if (ch >= '0' && ch <= '9') {
                if (dot) {
                    continue;
                }
                value = (hex ? (value << 4) : ((value << 3) + (value << 1))) + digits[ch];
            } else if (ch == '"' || ch == '\'') {
                if (quote) {
                    break;
                }
                throw new ConvertException("illegal escape(" + ch + ") (position = " + position + ")");
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
            } else if (quote && ch <= ' ') {
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

    public final String readFieldName() {
        return this.readSmallString();
    }

    @Override
    public DeMember readFieldName(final DeMemberInfo memberInfo) {
        final int eof = this.limit;
        if (this.position == eof) {
            return null;
        }
        DeMemberNode node = memberInfo.getMemberNode();
        char ch = nextGoodChar(true); // 需要跳过注释
        final char[] text0 = this.text;
        int currpos = this.position;
        if (ch == '"' || ch == '\'') {
            final char quote = ch;
            for (; ; ) {
                ch = text0[++currpos];
                if (ch == quote) {
                    break;
                }
                node = node == null ? null : node.getNode(ch);
            }
            this.position = currpos;
            return node == null ? null : node.getValue();
        } else {
            node = node == null ? null : node.getNode(ch);
            int start = currpos;
            for (; ; ) {
                if (currpos == eof) {
                    break;
                }
                ch = text0[++currpos];
                if (ch == ',' || ch == ']' || ch == '}' || ch <= ' ' || ch == ':') {
                    break;
                }
                node = node == null ? null : node.getNode(ch);
            }
            int len = currpos - start;
            if (len < 1) {
                this.position = currpos;
            } else {
                this.position = currpos - 1;
            }
            return node == null ? null : node.getValue();
        }
    }
    // ------------------------------------------------------------

    @Override
    public final boolean readBoolean() {
        return "true".equalsIgnoreCase(this.readSmallString());
    }

    @Override
    public final byte readByte() {
        return (byte) readInt();
    }

    @Override
    public final byte[] readByteArray() {
        int len = readArrayB(null, null, null);
        int contentLength = -1;
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        if (len == Reader.SIGN_NOLENBUTBYTES) {
            contentLength = readMemberContentLength(null, null);
            len = Reader.SIGN_NOLENGTH;
        }
        if (len == Reader.SIGN_NOLENGTH) {
            int size = 0;
            byte[] data = new byte[8];
            int startPosition = position();
            while (hasNext(startPosition, contentLength)) {
                if (size >= data.length) {
                    byte[] newdata = new byte[data.length + 4];
                    System.arraycopy(data, 0, newdata, 0, size);
                    data = newdata;
                }
                data[size++] = readByte();
            }
            readArrayE();
            byte[] newdata = new byte[size];
            System.arraycopy(data, 0, newdata, 0, size);
            return newdata;
        } else {
            byte[] values = new byte[len];
            for (int i = 0; i < values.length; i++) {
                values[i] = readByte();
            }
            readArrayE();
            return values;
        }
    }

    @Override
    public final char readChar() {
        return (char) readInt();
    }

    @Override
    public final short readShort() {
        return (short) readInt();
    }

    @Override
    public final float readFloat() {
        String chars = readSmallString();
        if (chars != null) {
            chars = chars.trim();
        }
        if (chars == null || chars.isEmpty()) {
            return 0.f;
        }
        switch (chars) {
            case "Infinity":
                return (float) Double.POSITIVE_INFINITY;
            case "-Infinity":
                return (float) Double.NEGATIVE_INFINITY;
            default:
                return Float.parseFloat(chars); // Float.parseFloat能识别NaN
        }
    }

    @Override
    public final double readDouble() {
        String chars = readSmallString();
        if (chars != null) {
            chars = chars.trim();
        }
        if (chars == null || chars.isEmpty()) {
            return 0.0;
        }
        switch (chars) {
            case "Infinity":
                return Double.POSITIVE_INFINITY;
            case "-Infinity":
                return Double.NEGATIVE_INFINITY;
            default:
                return Double.parseDouble(chars); // Double.parseDouble能识别NaN
        }
    }

    @Override
    public String readSmallString() {
        final int eof = this.limit;
        if (this.position == eof) {
            return null;
        }
        char ch = nextGoodChar(true); // 需要跳过注释
        final char[] text0 = this.text;
        int currpos = this.position;
        if (ch == '"' || ch == '\'') {
            final char quote = ch;
            final int start = currpos + 1;
            for (; ; ) {
                ch = text0[++currpos];
                if (ch == '\\') {
                    this.position = currpos - 1;
                    return readEscapeValue(quote, start);
                } else if (ch == quote) {
                    break;
                }
            }
            this.position = currpos;
            return new String(text0, start, currpos - start);
        } else {
            int start = currpos;
            for (; ; ) {
                if (currpos == eof) {
                    break;
                }
                ch = text0[++currpos];
                if (ch == ',' || ch == ']' || ch == '}' || ch <= ' ' || ch == ':') {
                    break;
                }
            }
            int len = currpos - start;
            if (len < 1) {
                this.position = currpos;
                return String.valueOf(ch);
            }
            this.position = currpos - 1;
            if (len == 4
                    && text0[start] == 'n'
                    && text0[start + 1] == 'u'
                    && text0[start + 2] == 'l'
                    && text0[start + 3] == 'l') {
                return null;
            }
            return new String(text0, start, len == eof ? (len + 1) : len);
        }
    }

    /**
     * 读取字符串， 必须是"或者'包围的字符串值
     *
     * @return String值
     */
    @Override
    public String readString() {
        return readString(true);
    }

    public final String readStringValue() {
        return readString(false);
    }

    protected String readString(boolean flag) {
        final char[] text0 = this.text;
        char expected = nextGoodChar(true);
        int currpos = this.position;
        if (expected != '"' && expected != '\'') {
            if (expected == 'n'
                    && text0.length > currpos + 3
                    && (text0[1 + currpos] == 'u' && text0[2 + currpos] == 'l' && text0[3 + currpos] == 'l')) {
                if (text0[++currpos] == 'u' && text0[++currpos] == 'l' && text0[++currpos] == 'l') {
                    this.position = currpos;
                    if (text0.length > currpos + 4) {
                        char ch = text0[currpos + 1];
                        if (ch == ',' || ch <= ' ' || ch == '}' || ch == ']' || (flag && ch == ':')) {
                            return null;
                        }
                        final int start = currpos - 3;
                        for (; ; ) {
                            if (currpos >= text0.length) {
                                break;
                            }
                            ch = text0[currpos];
                            if (ch == ',' || ch <= ' ' || ch == '}' || ch == ']' || (flag && ch == ':')) {
                                break;
                            }
                            currpos++;
                        }
                        if (currpos == start) {
                            throw new ConvertException("expected a string after a key but '" + text0[position]
                                    + "' (position = " + position + ") in (" + new String(this.text) + ")");
                        }
                        this.position = currpos - 1;
                        return new String(text0, start, currpos - start);
                    } else {
                        return null;
                    }
                }
            } else {
                final int start = currpos;
                for (; ; ) {
                    if (currpos >= text0.length) {
                        break;
                    }
                    char ch = text0[currpos];
                    if (ch == ',' || ch <= ' ' || ch == '}' || ch == ']' || (flag && ch == ':')) {
                        break;
                    }
                    currpos++;
                }
                if (currpos == start) {
                    throw new ConvertException("expected a string after a key but '" + text0[position]
                            + "' (position = " + position + ") in (" + new String(this.text) + ")");
                }
                this.position = currpos - 1;
                return new String(text0, start, currpos - start);
            }
            this.position = currpos;
            throw new ConvertException("expected a ':' after a key but '" + text0[position] + "' (position = "
                    + position + ") in (" + new String(this.text) + ")");
        }
        final int start = ++currpos;
        CharArray array = null;
        char c;
        for (; ; ) {
            char ch = text0[currpos];
            if (ch == expected) {
                break;
            } else if (ch == '\\') {
                if (array == null) {
                    array = array();
                    array.append(text0, start, currpos - start);
                }
                c = text0[++currpos];
                switch (c) {
                    case '"':
                    case '\'':
                    case '\\':
                    case '/':
                        array.append(c);
                        break;
                    case 'n':
                        array.append('\n');
                        break;
                    case 'r':
                        array.append('\r');
                        break;
                    case 'u':
                        array.append((char) Integer.parseInt(
                                new String(new char[] {
                                    text0[++currpos], text0[++currpos], text0[++currpos], text0[++currpos]
                                }),
                                16));
                        break;
                    case 't':
                        array.append('\t');
                        break;
                    case 'b':
                        array.append('\b');
                        break;
                    case 'f':
                        array.append('\f');
                        break;
                    default:
                        this.position = currpos;
                        throw new ConvertException("illegal escape(" + c + ") (position = " + this.position + ") in ("
                                + new String(this.text) + ")");
                }
            } else if (array != null) {
                array.append(ch);
            }
            currpos++;
        }
        this.position = currpos;
        return array != null ? array.toStringThenClear() : new String(text0, start, currpos - start);
    }

    private String readEscapeValue(final char expected, int start) {
        CharArray array = this.array();
        final char[] text0 = this.text;
        int pos = this.position;
        array.append(text0, start, pos + 1 - start);
        char c;
        for (; ; ) {
            c = text0[++pos];
            if (c == expected) {
                this.position = pos;
                return array.toStringThenClear();
            } else if (c == '\\') {
                c = text0[++pos];
                switch (c) {
                    case '"':
                    case '\'':
                    case '\\':
                    case '/':
                        array.append(c);
                        break;
                    case 'n':
                        array.append('\n');
                        break;
                    case 'r':
                        array.append('\r');
                        break;
                    case 'u':
                        array.append((char) Integer.parseInt(
                                new String(new char[] {text0[++pos], text0[++pos], text0[++pos], text0[++pos]}), 16));
                        break;
                    case 't':
                        array.append('\t');
                        break;
                    case 'b':
                        array.append('\b');
                        break;
                    case 'f':
                        array.append('\f');
                        break;
                    default:
                        this.position = pos;
                        throw new ConvertException("illegal escape(" + c + ") (position = " + this.position + ") in ("
                                + new String(this.text) + ")");
                }
            } else {
                array.append(c);
            }
        }
    }

    protected static final int[] digits = new int[255];

    static {
        for (int i = 0; i < digits.length; i++) {
            digits[i] = -1; // -1 错误
        }
        for (int i = '0'; i <= '9'; i++) {
            digits[i] = i - '0';
        }
        for (int i = 'a'; i <= 'f'; i++) {
            digits[i] = i - 'a' + 10;
        }
        for (int i = 'A'; i <= 'F'; i++) {
            digits[i] = i - 'A' + 10;
        }
        digits['"'] = digits['\''] = -2; // -2 跳过
        digits[' '] = digits['\t'] = digits['\r'] = digits['\n'] = -3; // -3可能跳过
        digits[','] = digits['}'] = digits[']'] = digits[':'] = -4; // -4退出
    }

    protected static class CharArray {

        private static final int DEFAULT_SIZE = 1024;

        private int count;

        private char[] content = new char[DEFAULT_SIZE];

        private char[] expand(int len) {
            int newcount = count + len;
            if (newcount <= content.length) {
                return content;
            }
            char[] newdata = new char[Math.max(content.length * 2, newcount)];
            System.arraycopy(content, 0, newdata, 0, count);
            this.content = newdata;
            return newdata;
        }

        public CharArray append(char[] str, int offset, int len) {
            char[] chs = expand(len);
            System.arraycopy(str, offset, chs, count, len);
            count += len;
            return this;
        }

        public CharArray append(char ch) {
            char[] chs = expand(1);
            chs[count++] = ch;
            return this;
        }

        public CharArray clear() {
            this.count = 0;
            return this;
        }

        public char[] content() {
            return content;
        }

        public int length() {
            return count;
        }

        public char[] getChars() {
            return Arrays.copyOfRange(content, 0, count);
        }

        public String toStringThenClear() {
            String s = toString();
            this.count = 0;
            return s;
        }

        @Override
        public String toString() {
            return new String(content, 0, count);
        }
    }
}
