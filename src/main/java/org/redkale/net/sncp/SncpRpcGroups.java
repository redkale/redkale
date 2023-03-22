/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.boot.ClassFilter;

/**
 * 协议地址组合对象, 对应application.xml 中 resources-&#62;group 节点信息
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class SncpRpcGroups {

    protected final Map<String, SncpRpcGroup> sncpRpcGroups = new ConcurrentHashMap<>();

    public SncpRpcGroup getSncpRpcGroup(String group) {
        return sncpRpcGroups.get(group);
    }

    public boolean containsGroup(String group) {
        return sncpRpcGroups.containsKey(group);
    }

    public SncpRpcGroup computeIfAbsent(String group, String protocol) {
        return sncpRpcGroups.computeIfAbsent(group, g -> new SncpRpcGroup(group, protocol));
    }

    public String getGroup(InetSocketAddress address) {
        for (SncpRpcGroup g : sncpRpcGroups.values()) {
            if (g.containsAddress(address)) {
                return g.getName();
            }
        }
        return null;
    }

    public boolean isLocalGroup(String sncpGroup, InetSocketAddress sncpAddress, ClassFilter.FilterEntry entry) {
        if (sncpGroup != null && !sncpGroup.isEmpty() && sncpGroup.equals(entry.getGroup())) {
            return true;
        }
        if (entry.isEmptyGroup()) {
            return true;
        } else if (entry.isRemote()) {
            return false;
        } else {
            SncpRpcGroup group = sncpRpcGroups.get(entry.getGroup());
            if (group == null) {
                throw new SncpException("Not found group(" + entry.getGroup() + ")");
            } else {
                return sncpAddress == null ? false : group.containsAddress(sncpAddress);
            }
        }
    }
}
