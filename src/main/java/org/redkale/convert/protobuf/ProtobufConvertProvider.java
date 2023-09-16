/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.protobuf;

import org.redkale.annotation.Priority;
import org.redkale.convert.*;

/**
 *
 * @author zhangjx
 */
@Priority(-900)
public class ProtobufConvertProvider implements ConvertProvider {

    @Override
    public ConvertType type() {
        return ConvertType.PROTOBUF;
    }

    @Override
    public Convert convert() {
        return ProtobufConvert.root();
    }

}
