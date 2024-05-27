/*
 */
package org.redkale.inject;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * 自定义注入加载器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface ResourceTypeLoader {

	public Object load(
			ResourceFactory factory,
			String srcResourceName,
			Object srcObj,
			String resourceName,
			Field field,
			Object attachment);

	public Type resourceType();

	// 返回true:  表示调用ResourceLoader之后资源仍不存在，则会在ResourceFactory里注入默认值null。
	// 返回false: 表示资源不存在下次仍会调用ResourceLoader自行处理。
	default boolean autoNone() {
		return true;
	}
}
