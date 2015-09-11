/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

import com.wentch.redkale.net.icep.attr.*;
import com.wentch.redkale.net.icep.stun.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.nio.*;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class BindingIcepServlet extends IcepServlet {

    @Override
    public void execute(IcepRequest request, IcepResponse response) throws IOException {
        StunPacket packet = request.getStunPacket();
        ByteBuffer buffer = response.getContext().pollBuffer();
        packet.addAttribute(new XorMappedAddressAttribute(request.getRemoteAddress()));
        packet.addAttribute(new MappedAddressAttribute(request.getRemoteAddress()));
        packet.getHeader().setRequestid((StunHeader.TYPE_SUCCESS | StunHeader.ACTION_BINDING));
        packet.encode(buffer);
        buffer.flip();
        Utility.println("响应结果: ", buffer);
        response.finish(buffer);
    }

}
