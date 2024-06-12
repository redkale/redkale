/*

*/

package org.redkale.source;

import org.redkale.annotation.Comment;
import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.Column;

/**
 *
 * @author zhangjx
 */
public class EntityColumn {

    private final boolean primary; // 是否主键

    private final String field;

    private final String column;

    private final Class type;

    private final String comment;

    private final boolean nullable;

    private final boolean unique;

    private final int length;

    private final int precision;

    private final int scale;

    public EntityColumn(boolean primary, Column col, String name, Class type, Comment comment) {
        this.primary = primary;
        this.field = name;
        this.column = col == null || col.name().isEmpty() ? name : col.name();
        this.type = type;
        this.comment = (col == null || col.comment().isEmpty())
                        && comment != null
                        && !comment.value().isEmpty()
                ? comment.value()
                : (col == null ? "" : col.comment());
        this.nullable = col != null && col.nullable();
        this.unique = col != null && col.unique();
        this.length = col == null ? 255 : col.length();
        this.precision = col == null ? 0 : col.precision();
        this.scale = col == null ? 0 : col.scale();
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getField() {
        return field;
    }

    public String getColumn() {
        return column;
    }

    public Class getType() {
        return type;
    }

    public String getComment() {
        return comment;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isUnique() {
        return unique;
    }

    public int getLength() {
        return length;
    }

    public int getPrecision() {
        return precision;
    }

    public int getScale() {
        return scale;
    }
}
