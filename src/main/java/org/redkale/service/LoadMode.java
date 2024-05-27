/*
 *
 */
package org.redkale.service;

/**
 * Service加载模式
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public enum LoadMode {
    /** 本地模式 */
    LOCAL,
    /** 远程模式 */
    REMOTE,
    /** 任意模式 */
    ANY;

    /**
     * 是否匹配当前模式
     *
     * @param mode 模式
     * @return 是否匹配
     */
    public boolean matches(LoadMode mode) {
        return this == mode || this == ANY;
    }

    /**
     * 是否匹配当前模式
     *
     * @param remote 是否远程
     * @param mode 模式
     * @return 是否匹配
     */
    public static boolean matches(boolean remote, LoadMode mode) {
        if (remote && mode == LoadMode.LOCAL) { // 只容许本地模式
            return false;
        } else if (!remote && mode == LoadMode.REMOTE) { // 只容许远程模式
            return false;
        }
        return true;
    }
}
