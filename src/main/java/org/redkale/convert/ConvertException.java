/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.util.RedkaleException;

/**
 * 序列化自定义异常类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class ConvertException extends RedkaleException {

    public ConvertException() {
        super();
    }

    public ConvertException(String s) {
        super(s);
    }

    public ConvertException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConvertException(Throwable cause) {
        super(cause);
    }
}
