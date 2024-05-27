/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BinaryOperator;

/**
 * 简单的xml读取器, 只读element节点信息，其他信息(如: namespace、comment、docdecl等)都会丢弃
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class XmlReader {

    protected int position = -1;

    private char[] text;

    private int limit;

    private Deque<TagNode> tags = new ArrayDeque<>();

    protected int lineNumber;

    protected int columnNumber;

    protected BinaryOperator<String> attrFunc;

    private static class TagNode {

        public String tag;

        public AnyValueWriter config;

        public TagNode(String tag, AnyValueWriter node) {
            this.tag = tag;
            this.config = node;
        }
    }

    public XmlReader(String text) {
        this(Utility.charArray(text));
    }

    public XmlReader(char[] text) {
        setText(text, 0, text.length);
    }

    public XmlReader(char[] text, int start, int len) {
        setText(text, start, len);
    }

    private void setText(char[] text, int start, int len) {
        this.text = text;
        this.position = start - 1;
        this.limit = start + len - 1;
    }

    public XmlReader attrFunc(BinaryOperator<String> func) {
        this.attrFunc = func;
        return this;
    }

    public AnyValue read() {
        AnyValueWriter root = AnyValueWriter.create();
        char ch;
        lineNumber++;
        ByteArray array = new ByteArray(128);
        while ((ch = nextGoodChar()) > 0) {
            if (ch == '<') {
                char ch2 = nextChar();
                if (ch2 == '!') {
                    char ch3 = nextChar();
                    if (ch3 == '-') {
                        readComment();
                    } else if (ch3 == '[') {
                        readCDSect(root, array);
                    } else if (ch3 == 'D') {
                        readDocdecl(root, array);
                    } else {
                        throw newException("unexpected character in markup " + ch3);
                    }
                } else if (ch2 == '?') {
                    readXmlDecl(root, array);
                } else if (ch2 == '/') { // 节点结束
                    String tag = readEndTag();
                    if (tags.isEmpty()) {
                        throw newException("unexpected end tag " + tag);
                    }
                    if (!tags.getFirst().tag.equals(tag)) {
                        throw newException("expected end tag " + tags.getFirst().tag + " but " + tag);
                    }
                    tags.removeFirst();
                    if (tags.isEmpty()) {
                        break;
                    }
                } else { // 节点开始
                    readStartTag(root);
                }
            } else {
                int start = this.position;
                for (; ; ) {
                    ch = nextChar();
                    if (ch == '<') {
                        backChar(ch);
                        break;
                    }
                }
                String content = escape(new String(this.text, start, this.position + 1 - start));
                if (tags.isEmpty()) {
                    root.addValue(AnyValue.XML_TEXT_NODE_NAME, content);
                } else {
                    tags.getFirst().config.addValue(AnyValue.XML_TEXT_NODE_NAME, content);
                }
            }
        }
        return root;
    }

    /**
     * 读取下一个字符， 不跳过空白字符
     *
     * @return 空白字符或有效字符
     */
    protected char nextChar() {
        if (this.position == this.limit) {
            throw newException("read EOF");
        }
        char ch = this.text[++this.position];
        if (ch == '\n') {
            this.lineNumber++;
            this.columnNumber = 0;
        }
        this.columnNumber++;
        return ch;
    }

    /**
     * 跳过空白字符， 返回一个非空白字符
     *
     * @return 有效字符
     */
    protected char nextGoodChar() {
        char c = nextChar();
        if (c > ' ') {
            return c;
        }
        for (; ; ) {
            c = nextChar();
            if (c > ' ') {
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

    // 返回是否endtag， 即以 />结尾
    protected boolean readTagAttribute(String tag, AnyValueWriter config) {
        boolean first = true;
        boolean endtag = false;
        boolean endattr = false;
        char ch = nextGoodChar();
        if (ch == '/') {
            return true;
        }
        int start = this.position;
        for (; ; ) {
            if (ch == '=') {
                break;
            } else if (ch >= '0' && ch <= '9') {
                if (first) {
                    throw newException("illegal character " + ch);
                }
            } else if (ch >= 'a' && ch <= 'z') {
                // do nothing
            } else if (ch >= 'A' && ch <= 'Z') {
                // do nothing
            } else if (ch == '.' || ch == '-' || ch == '_' || ch == ':') {
                if (first) {
                    throw newException("illegal character " + ch);
                }
            } else {
                throw newException("illegal character " + ch);
            }
            ch = nextChar();
            first = false;
        }
        String attrName = new String(this.text, start, this.position - start);
        String attrValue;
        ch = nextGoodChar();
        if (ch == '"' || ch == '\'') {
            final char quote = ch;
            start = this.position + 1;
            for (; ; ) {
                ch = nextChar();
                if (ch == quote) {
                    break;
                }
            }
            attrValue = escape(new String(this.text, start, this.position - start));
        } else {
            ch = nextGoodChar();
            start = this.position;
            for (; ; ) {
                if (ch == '/') {
                    endtag = true;
                    break;
                }
                if (ch == '>') {
                    endattr = true;
                    break;
                } else if (ch <= ' ') {
                    break;
                }
                ch = nextChar();
            }
            attrValue = escape(new String(this.text, start, this.position - start));
        }
        if (attrFunc != null) {
            attrValue = attrFunc.apply(attrName, attrValue);
        }
        config.addValue(attrName, attrValue);
        if (endtag) {
            return endtag;
        }
        if (endattr) {
            return false;
        }
        ch = nextGoodChar();
        if (ch == '>') {
            return false;
        }
        if (ch == '/') {
            return true;
        }
        backChar(ch);
        return readTagAttribute(tag, config);
    }

    protected void readStartTag(AnyValueWriter root) {
        final int start = this.position;
        boolean hasattr = false;
        boolean endtag = false;

        char ch = nextGoodChar();
        for (; ; ) {
            if (ch == '>') {
                break;
            } else if (ch == '/') {
                endtag = true;
                break;
            } else if (ch <= ' ') {
                hasattr = true;
                break;
            }
            ch = nextChar();
        }
        final String tag = new String(this.text, start, this.position - start).trim();
        AnyValueWriter config = AnyValueWriter.create();
        TagNode tagNode = new TagNode(tag, config);
        if (tags.isEmpty()) {
            root.addValue(tag, tagNode.config);
        } else {
            tags.getFirst().config.addValue(tag, tagNode.config);
        }
        if (hasattr) {
            endtag = readTagAttribute(tag, config);
        }
        if (endtag) {
            if (nextChar() != '>') {
                throw newException("expected /> for tag end");
            }
            return;
        }
        tags.addFirst(tagNode);
    }

    protected String readEndTag() {
        final int start = this.position + 1; // 跳过'/'
        char ch;
        for (; ; ) {
            ch = nextChar();
            if (ch == '>') {
                break;
            }
        }
        return new String(this.text, start, this.position - start).trim();
    }

    protected void readComment() { // 读取到 <!- 才进入此方法
        char ch = nextChar();
        if (ch != '-') {
            throw newException("expected <!-- for comment start");
        }
        int dash = 0;
        for (; ; ) {
            ch = nextChar();
            if (ch == '>' && dash >= 2) {
                break;
            } else if (ch == '-') {
                dash++;
            } else {
                dash = 0;
            }
        }
    }

    protected void readDocdecl(
            AnyValueWriter root,
            ByteArray array) { // 读取到 <!D 才进入此方法  '<!DOCTYPE' S Name (S ExternalID)? S? ('[' (markupdecl | DeclSep)* ']'
        // S?)? '>'
        if (nextChar() != 'O') {
            throw newException("expected <!DOCTYPE");
        }
        if (nextChar() != 'C') {
            throw newException("expected <!DOCTYPE");
        }
        if (nextChar() != 'T') {
            throw newException("expected <!DOCTYPE");
        }
        if (nextChar() != 'Y') {
            throw newException("expected <!DOCTYPE");
        }
        if (nextChar() != 'P') {
            throw newException("expected <!DOCTYPE");
        }
        if (nextChar() != 'E') {
            throw newException("expected <!DOCTYPE");
        }
        char ch;
        array.clear();
        array.put("<!DOCTYPE".getBytes());
        for (; ; ) {
            ch = nextChar();
            if (ch == '>') {
                array.putByte(ch);
                root.setValue("", array.toString(StandardCharsets.UTF_8));
                break;
            }
            array.putByte(ch);
        }
    }

    protected void readCDSect(
            AnyValueWriter root, ByteArray array) { // 读取到 <![ 才进入此方法  '<![CDATA[ (Char* - (Char* ']]>' Char*)) ]]>'
        if (nextChar() != 'C') {
            throw newException("expected <![CDATA[ for cdsect start");
        }
        if (nextChar() != 'D') {
            throw newException("expected <![CDATA[ for cdsect start");
        }
        if (nextChar() != 'A') {
            throw newException("expected <![CDATA[ for cdsect start");
        }
        if (nextChar() != 'T') {
            throw newException("expected <![CDATA[ for cdsect start");
        }
        if (nextChar() != 'A') {
            throw newException("expected <![CDATA[ for cdsect start");
        }
        if (nextChar() != '[') {
            throw newException("expected <![CDATA[ for cdsect start");
        }
        char ch;
        array.clear();
        array.put("<![CDATA[".getBytes());
        for (; ; ) {
            ch = nextChar();
            if (ch == '>') {
                if (this.text[this.position - 1] != ']' && this.text[this.position - 2] != ']') {
                    throw newException("in cdsect after two dashes (]]) next character must be >");
                }
                array.putByte(ch);
                root.setValue("", array.toString(StandardCharsets.UTF_8));
                break;
            }
            array.putByte(ch);
        }
    }

    protected void readXmlDecl(
            AnyValueWriter root, ByteArray array) { // 读取到 <? 才进入此方法  <?xml version="1.0" encoding="UTF-8"?>
        char ch;
        array.clear();
        array.putByte('<');
        array.putByte('?');
        for (; ; ) {
            ch = nextChar();
            if (ch == '>') {
                if (this.text[this.position - 1] != '?') {
                    throw newException("in xmldecl after ? next character must be >");
                }
                array.putByte(ch);
                root.setValue("", array.toString(StandardCharsets.UTF_8));
                break;
            }
            array.putByte(ch);
        }
    }

    protected static String escape(String value) {
        return value.replace("&quot;", "/")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    protected RedkaleException newException(String msg) {
        return newException(msg, (Throwable) null);
    }

    protected RedkaleException newException(String msg, Throwable chain) {
        return new RedkaleException((msg == null ? "" : (msg + " "))
                + "(line: " + this.lineNumber + ", column: " + this.columnNumber + ", position:" + this.position + ") "
                + (chain == null ? "" : ("caused by: " + chain)));
    }
}
