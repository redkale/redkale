/*

*/

package org.redkale.convert;

import org.redkale.util.Attribute;

/**
 *
 * @author zhangjx
 * @param <F> 字段类型
 */
public interface ConvertColumnTransfer<F> {
    public <A> A transfer(Object obj, Attribute attribute, F value);
}
