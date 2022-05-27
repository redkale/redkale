/*
 */
package org.redkale.test.util;

import java.util.Properties;
import org.junit.jupiter.api.*;
import org.redkale.util.AnyValue;

/**
 *
 * @author zhangjx
 */
public class AnyValuePropertiesTest {

    @Test
    public void run1() {
        Properties properties = new Properties();
        properties.put("redkale.aaa.ooo", "value o");
        properties.put("redkale.aaa.ppp", "value p");
        properties.put("redkale.bbb.qqq.rrr", "value r");
        properties.put("redkale.bbb.sss", "value s");
        properties.put("redkale.source[my].sss", "my s");
        properties.put("redkale.source[my].ttt", "my t");
        properties.put("redkale.source[you].sss", "you s");
        properties.put("redkale.source[you].ttt", "you t");
        properties.put("redkale.ddd[2].ww", "ww 2");
        properties.put("redkale.ddd[2].nn", "nn 2");
        properties.put("redkale.ddd[0].ww", "ww 0");
        properties.put("redkale.ddd[0].nn", "nn 0");
        properties.put("redkale.ddd[10].ww", "ww 10");
        properties.put("redkale.ddd[10].nn", "nn 10");
        properties.put("redkale.mmm.node[5]", "n5");
        properties.put("redkale.mmm.node[0]", "n0");
        properties.put("redkale.mmm.node[20]", "n20");

        String result = "{\r\n"
            + "    'redkale': '{\r\n"
            + "        'source': '{\r\n"
            + "            'my': '{\r\n"
            + "                'sss': 'my s',\r\n"
            + "                'ttt': 'my t',\r\n"
            + "            }',\r\n"
            + "            'you': '{\r\n"
            + "                'ttt': 'you t',\r\n"
            + "                'sss': 'you s',\r\n"
            + "            }',\r\n"
            + "        }',\r\n"
            + "        'ddd': '{\r\n"
            + "            'ww': 'ww 0',\r\n"
            + "            'nn': 'nn 0',\r\n"
            + "        }',\r\n"
            + "        'ddd': '{\r\n"
            + "            'ww': 'ww 2',\r\n"
            + "            'nn': 'nn 2',\r\n"
            + "        }',\r\n"
            + "        'ddd': '{\r\n"
            + "            'ww': 'ww 10',\r\n"
            + "            'nn': 'nn 10',\r\n"
            + "        }',\r\n"
            + "        'mmm': '{\r\n"
            + "            'node': 'n0',\r\n"
            + "            'node': 'n5',\r\n"
            + "            'node': 'n20',\r\n"
            + "        }',\r\n"
            + "        'bbb': '{\r\n"
            + "            'sss': 'value s',\r\n"
            + "            'qqq': '{\r\n"
            + "                'rrr': 'value r',\r\n"
            + "            }',\r\n"
            + "        }',\r\n"
            + "        'aaa': '{\r\n"
            + "            'ppp': 'value p',\r\n"
            + "            'ooo': 'value o',\r\n"
            + "        }',\r\n"
            + "    }',\r\n"
            + "}";
        Assertions.assertEquals(result, AnyValue.loadFromProperties(properties).toString());
    }
}
