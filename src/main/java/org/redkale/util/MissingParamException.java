/*
 *
 */
package org.redkale.util;

/**
 * 缺失参数异常类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class MissingParamException extends RedkaleException {

    private String parameter;

    public MissingParamException() {
        super();
    }

    public MissingParamException(String parameter) {
        super("Missing parameter " + parameter);
        this.parameter = parameter;
    }

    public MissingParamException(String parameter, Throwable cause) {
        super("Missing parameter " + parameter, cause);
        this.parameter = parameter;
    }

    public MissingParamException(Throwable cause) {
        super(cause);
    }

    public String getParameter() {
        return parameter;
    }

    public static MissingParamException of(String parameter) {
        return new MissingParamException(parameter);
    }

}
