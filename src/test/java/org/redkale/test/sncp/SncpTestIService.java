/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import java.util.concurrent.CompletableFuture;
import org.redkale.service.Service;

/**
 *
 * @author zhangjx
 */
public interface SncpTestIService extends Service {

    public String queryResult(SncpTestBean bean);

    public double queryDoubleResult(String a, int b, double value);

    public long queryLongResult(String a, int b, long value);

    public CompletableFuture<String> queryResultAsync(SncpTestBean bean);

    public SncpTestBean insert(SncpTestBean bean);

    public String updateBean(SncpTestBean bean);
}
