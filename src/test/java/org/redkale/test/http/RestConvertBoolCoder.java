/*

*/

package org.redkale.test.http;

import org.redkale.convert.Reader;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;

/**
 *
 * @author zhangjx
 * @param <R> Reader
 * @param <W> Writer
 */
public class RestConvertBoolCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Boolean> {

    @Override
    public void convertTo(W out, Boolean value) {
        out.writeInt(value == null || !value ? 0 : 1);
    }

    @Override
    public Boolean convertFrom(R in) {
        return in.readInt() == 1;
    }
}
