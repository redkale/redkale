/*
 *
 */
package org.redkale.util;

import java.io.Serializable;
import java.util.function.BiConsumer;

/**
 * Lambda的BiConsumer自定义类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 * @param <T> 泛型
 * @param <R> 泛型
 */
@FunctionalInterface
public interface LambdaBiConsumer<T, R> extends BiConsumer<T, R>, Serializable {

	public static <T> String readColumn(LambdaBiConsumer<T, ?> consumer) {
		return Utility.readFieldName(consumer);
	}

	public static <T> Class<T> readClass(LambdaBiConsumer<T, ?> consumer) {
		return Utility.readClassName(consumer);
	}
}
