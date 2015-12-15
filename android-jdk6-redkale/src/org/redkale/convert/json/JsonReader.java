/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.redkale.convert.*;
import static org.redkale.convert.Reader.*;
import org.redkale.util.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public final class JsonReader implements Reader {

    private int position = -1;

    private char[] text;

    private int limit;

    public static ObjectPool<JsonReader> createPool(int max) {
        return new ObjectPool<JsonReader>(max, new Creator<JsonReader>() {  //为了兼容 JDK 6

            @Override
            public JsonReader create(Object... params) {
                return new JsonReader();
            }
        }, null, new Predicate<JsonReader>() {

            @Override
            public boolean test(JsonReader t) {
                return t.recycle();
            }
        });
    }

    public JsonReader() {
    }

    public JsonReader(String json) {
        setText(Utility.charArray(json));
    }

    public JsonReader(char[] text) {
        setText(text, 0, text.length);
    }

    public JsonReader(char[] text, int start, int len) {
        setText(text, start, len);
    }

    public final void setText(String text) {
        JsonReader.this.setText(Utility.charArray(text));
    }

    public final void setText(char[] text) {
        setText(text, 0, text.length);
    }

    public final void setText(char[] text, int start, int len) {
        this.text = text;
        this.position = start - 1;
        this.limit = start + len - 1;
    }

    protected boolean recycle() {
        this.position = -1;
        this.limit = -1;
        this.text = null;
        return true;
    }

    public void close() {
        this.recycle();
    }

    /**
     * 找到指定的属性值 例如: {id : 1, data : { name : 'a', items : [1,2,3]}} seek('data.items') 直接跳转到 [1,2,3];
     *
     * @param key
     */
    public final void seek(String key) {
        if (key == null || key.length() < 1) return;
        final String[] keys = key.split("\\.");
        nextGoodChar(); //读掉 { [
        for (String key1 : keys) {
            while (this.hasNext()) {
                String field = this.readSmallString();
                skipBlank();
                if (key1.equals(field)) break;
                skipValue();
            }
        }

    }

    /**
     * 跳过属性的值
     */
    @Override
    public final void skipValue() {
        final char ch = nextGoodChar();
        if (ch == '"' || ch == '\'') {
            backChar(ch);
            readString();
        } else if (ch == '{') {
            while (hasNext()) {
                this.readSmallString(); //读掉field
                this.skipBlank();
                this.skipValue();
            }
        } else if (ch == '[') {
            while (hasNext()) {
                this.skipValue();
            }
        } else {
            char c;
            for (;;) {
                c = nextChar();
                if (c <= ' ') return;
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
     * @return
     */
    protected char nextChar() {
        return this.text[++this.position];
    }

    /**
     * 跳过空白字符， 返回一个非空白字符
     *
     * @return
     */
    protected char nextGoodChar() {
        char c = nextChar();
        if (c > ' ') return c;
        for (;;) {
            c = nextChar();
            if (c > ' ') return c;
        }
    }

    /**
     * 回退最后读取的字符
     *
     * @param ch
     */
    protected void backChar(char ch) {
        this.position--;
    }

    /**
     * 判断下一个非空白字符是否为{
     *
     */
    @Override
    public int readObjectB() {
        char ch = this.text[++this.position];
        if (ch == '{') return SIGN_NOLENGTH;
        if (ch <= ' ') {
            for (;;) {
                ch = this.text[++this.position];
                if (ch > ' ') break;
            }
            if (ch == '{') return SIGN_NOLENGTH;
        }
        if (ch == 'n' && text[++position] == 'u' && text[++position] == 'l' && text[++position] == 'l') return SIGN_NULL;
        if (ch == 'N' && text[++position] == 'U' && text[++position] == 'L' && text[++position] == 'L') return SIGN_NULL;
        throw new ConvertException("a json object text must begin with '{' (position = " + position + ") but '" + ch + "' in (" + new String(this.text) + ")");
    }

    @Override
    public void readObjectE() {
    }

    /**
     * 判断下一个非空白字符是否为{
     *
     */
    @Override
    public int readMapB() {
        return readArrayB();
    }

    @Override
    public void readMapE() {
    }

    /**
     * 判断下一个非空白字符是否为[
     *
     * @return
     */
    @Override
    public int readArrayB() {
        char ch = this.text[++this.position];
        if (ch == '[') return SIGN_NOLENGTH;
        if (ch == '{') return SIGN_NOLENGTH;
        if (ch <= ' ') {
            for (;;) {
                ch = this.text[++this.position];
                if (ch > ' ') break;
            }
            if (ch == '[') return SIGN_NOLENGTH;
            if (ch == '{') return SIGN_NOLENGTH;
        }
        if (ch == 'n' && text[++position] == 'u' && text[++position] == 'l' && text[++position] == 'l') return SIGN_NULL;
        if (ch == 'N' && text[++position] == 'U' && text[++position] == 'L' && text[++position] == 'L') return SIGN_NULL;
        throw new ConvertException("a json array text must begin with '[' (position = " + position + ") but '" + ch + "' in (" + new String(this.text) + ")");
    }

    @Override
    public void readArrayE() {
    }

    /**
     * 判断下一个非空白字符是否:
     */
    @Override
    public void skipBlank() {
        char ch = this.text[++this.position];
        if (ch == ':') return;
        if (ch <= ' ') {
            for (;;) {
                ch = this.text[++this.position];
                if (ch > ' ') break;
            }
            if (ch == ':') return;
        }
        throw new ConvertException("'" + new String(text) + "'expected a ':' but '" + ch + "'(position = " + position + ") in (" + new String(this.text) + ")");
    }

    /**
     * 判断对象是否存在下一个属性或者数组是否存在下一个元素
     *
     * @return
     */
    @Override
    public boolean hasNext() {
        char ch = this.text[++this.position];
        if (ch == ',') return true;
        if (ch == '}' || ch == ']') return false;
        if (ch <= ' ') {
            for (;;) {
                ch = this.text[++this.position];
                if (ch > ' ') break;
            }
            if (ch == ',') return true;
            if (ch == '}' || ch == ']') return false;
        }
        this.position--;
        return true;
    }

    @Override
    public String readClassName() {
        return null;
    }

    @Override
    public String readSmallString() {
        final int eof = this.limit;
        if (this.position == eof) return null;
        final char[] text0 = this.text;
        int currpos = this.position;
        char ch = text0[++currpos];
        if (ch <= ' ') {
            for (;;) {
                ch = text0[++currpos];
                if (ch > ' ') break;
            }
        }
        if (ch == '"' || ch == '\'') {
            final char quote = ch;
            final int start = currpos + 1;
            for (;;) {
                ch = text0[++currpos];
                if (ch == '\\') {
                    this.position = currpos - 1;
                    return readEscapeValue(quote, start);
                } else if (ch == quote) {
                    break;
                }
            }
            this.position = currpos;
            char[] chs = new char[currpos - start];
            System.arraycopy(text0, start, chs, 0, chs.length);
            return new String(chs);
        } else {
            int start = currpos;
            for (;;) {
                if (currpos == eof) break;
                ch = text0[++currpos];
                if (ch == ',' || ch == ']' || ch == '}' || ch <= ' ' || ch == ':') break;
            }
            int len = currpos - start;
            if (len < 1) {
                this.position = currpos;
                return String.valueOf(ch);
            }
            this.position = currpos - 1;
            if (len == 4 && text0[start] == 'n' && text0[start + 1] == 'u' && text0[start + 2] == 'l' && text0[start + 3] == 'l') return null;
            return new String(text0, start, len);
        }
    }

    /**
     * 读取一个int
     *
     * @return
     */
    @Override
    public final int readInt() {
        final char[] text0 = this.text;
        final int eof = this.limit;
        int currpos = this.position;
        char firstchar = text0[++currpos];
        if (firstchar <= ' ') {
            for (;;) {
                firstchar = text0[++currpos];
                if (firstchar > ' ') break;
            }
        }
        if (firstchar == '"' || firstchar == '\'') {
            firstchar = text0[++currpos];
            if (firstchar == '"' || firstchar == '\'') {
                this.position = currpos;
                return 0;
            }
        }
        int value = 0;
        final boolean negative = firstchar == '-';
        if (!negative) {
            if (firstchar < '0' || firstchar > '9') throw new NumberFormatException("illegal escape(" + firstchar + ") (position = " + currpos + ") in (" + new String(this.text) + ")");
            value = firstchar - '0';
        }
        for (;;) {
            if (currpos == eof) break;
            char ch = text0[++currpos];
            if (ch >= '0' && ch <= '9') {
                value = (value << 3) + (value << 1) + (ch - '0');
            } else if (ch == '"' || ch == '\'') {
            } else if (ch == ',' || ch == '}' || ch == ']' || ch <= ' ' || ch == ':') {
                break;
            } else {
                throw new NumberFormatException("illegal escape(" + ch + ") (position = " + currpos + ") in (" + new String(this.text) + ")");
            }
        }
        this.position = currpos - 1;
        return negative ? -value : value;
    }

    /**
     * 读取一个long
     *
     * @return
     */
    @Override
    public final long readLong() {
        final char[] text0 = this.text;
        final int eof = this.limit;
        int currpos = this.position;
        char firstchar = text0[++currpos];
        if (firstchar <= ' ') {
            for (;;) {
                firstchar = text0[++currpos];
                if (firstchar > ' ') break;
            }
        }
        if (firstchar == '"' || firstchar == '\'') {
            firstchar = text0[++currpos];
            if (firstchar == '"' || firstchar == '\'') {
                this.position = currpos;
                return 0L;
            }
        }
        long value = 0;
        final boolean negative = firstchar == '-';
        if (!negative) {
            if (firstchar < '0' || firstchar > '9') throw new NumberFormatException("illegal escape(" + firstchar + ") (position = " + currpos + ") in (" + new String(this.text) + ")");
            value = firstchar - '0';
        }
        for (;;) {
            if (currpos == eof) break;
            char ch = text0[++currpos];
            if (ch >= '0' && ch <= '9') {
                value = (value << 3) + (value << 1) + (ch - '0');
            } else if (ch == '"' || ch == '\'') {
            } else if (ch == ',' || ch == '}' || ch == ']' || ch <= ' ' || ch == ':') {
                break;
            } else {
                throw new NumberFormatException("illegal escape(" + ch + ") (position = " + currpos + ") but '" + ch + "' in (" + new String(this.text) + ")");
            }
        }
        this.position = currpos - 1;
        return negative ? -value : value;
    }

    @Override
    public DeMember readField(final AtomicInteger index, final DeMember[] members) {
        final String exceptedfield = this.readSmallString();
        final int len = members.length;
        int v = index.get();
        if (v >= len) {
            v = 0;
            index.set(0);
        }
        for (int k = v; k < len; k++) {
            if (exceptedfield.equals(members[k].getAttribute().field())) {
                index.set(k);
                return members[k];
            }
        }
        for (int k = 0; k < v; k++) {
            if (exceptedfield.equals(members[k].getAttribute().field())) {
                index.set(k);
                return members[k];
            }
        }
        return null;
        //if (result == null && len == 1 && text0[start] == '@') return REFER;
    }
//------------------------------------------------------------

    @Override
    public boolean readBoolean() {
        return "true".equalsIgnoreCase(this.readSmallString());
    }

    @Override
    public byte readByte() {
        return (byte) readInt();
    }

    @Override
    public char readChar() {
        return (char) readInt();
    }

    @Override
    public short readShort() {
        return (short) readInt();
    }

    @Override
    public float readFloat() {
        String chars = readSmallString();
        if (chars == null || chars.isEmpty()) return 0.f;
        return Float.parseFloat(chars);
    }

    @Override
    public double readDouble() {
        String chars = readSmallString();
        if (chars == null || chars.isEmpty()) return 0.0;
        return Double.parseDouble(chars);
    }

    /**
     * 读取字符串， 必须是"或者'包围的字符串值
     *
     * @return
     */
    @Override
    public String readString() {
        final char[] text0 = this.text;
        int currpos = this.position;
        char expected = text0[++currpos];
        if (expected <= ' ') {
            for (;;) {
                expected = text0[++currpos];
                if (expected > ' ') break;
            }
        }
        if (expected != '"' && expected != '\'') {
            if (expected == 'n' && text0.length > currpos + 3) {
                if (text0[++currpos] == 'u' && text0[++currpos] == 'l' && text0[++currpos] == 'l') {
                    this.position = currpos;
                    if (text0.length > currpos + 4) {
                        char ch = text0[currpos + 1];
                        if (ch == ',' || ch <= ' ' || ch == '}' || ch == ']' || ch == ':') return null;
                    } else {
                        return null;
                    }
                }
            } else {
                final int start = currpos;
                for (;;) {
                    char ch = text0[currpos];
                    if (ch == ',' || ch <= ' ' || ch == '}' || ch == ']' || ch == ':') break;
                    currpos++;
                }
                if (currpos == start) throw new ConvertException("expected a string after a key but '" + text0[position] + "' (position = " + position + ") in (" + new String(this.text) + ")");
                this.position = currpos - 1;
                return new String(text0, start, currpos - start);
            }
            this.position = currpos;
            throw new ConvertException("expected a ':' after a key but '" + text0[position] + "' (position = " + position + ") in (" + new String(this.text) + ")");
        }
        final int start = ++currpos;
        for (;;) {
            char ch = text0[currpos];
            if (ch == expected) {
                break;
            } else if (ch == '\\') {
                this.position = currpos - 1;
                return readEscapeValue(expected, start);
            }
            currpos++;
        }
        this.position = currpos;
        return new String(text0, start, currpos - start);
    }

    private String readEscapeValue(final char expected, int start) {
        StringBuilder array = new StringBuilder();
        final char[] text0 = this.text;
        int pos = this.position;
        array.append(text0, start, pos + 1 - start);
        char c;
        for (;;) {
            c = text0[++pos];
            if (c == expected) {
                this.position = pos;
                return array.toString();
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
                        array.append((char) Integer.parseInt(new String(new char[]{text0[++pos], text0[++pos], text0[++pos], text0[++pos]}), 16));
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
                        throw new ConvertException("illegal escape(" + c + ") (position = " + this.position + ") in (" + new String(this.text) + ")");
                }
            } else {
                array.append(c);
            }
        }
    }

}
