/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import org.redkale.annotation.Serial;

/**
 * FilterBean用于过滤条件， 所有的FilterBean都必须可以转换成FilterNode <br>
 * 标记为&#64;FilterColumn.ignore=true 的字段会被忽略， 不参与生成过滤条件 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.source.FilterColumn
 * @see org.redkale.source.FilterJoinColumn
 * @see org.redkale.source.FilterGroup
 * @author zhangjx
 */
@Serial
public interface FilterBean {}
