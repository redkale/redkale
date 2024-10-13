/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;

/**
 *
 * @author zhangjx
 */
@State(Scope.Thread)
public class BenchmarkConvertTest {

    private SimpleEntity entry;

    @Setup
    public void setup() {
        entry = SimpleEntity.create();
    }

    @TearDown
    public void tearDown() {
        entry = null;
    }

    @Benchmark
    public void testA_Json() {
        JsonConvert.root().convertTo(SimpleEntity.class, entry);
    }

    @Benchmark
    public void testB_Protobuf() {
        ProtobufConvert.root().convertTo(SimpleEntity.class, entry);
    }

//    @Test
//    public void testBenchmark() throws Exception {
//        Options options = new OptionsBuilder()
//                .include(BenchmarkConvertTest.class.getSimpleName())
//                .forks(1)
//                .threads(1)
//                .warmupIterations(1)
//                .measurementIterations(1)
//                .mode(Mode.Throughput)
//                .build();
//        new Runner(options).run();
//    }

    public static void main(String[] args) throws Exception {
        Options options = new OptionsBuilder()
                .include(BenchmarkConvertTest.class.getSimpleName())
                .forks(1)
                .threads(1)
                .warmupIterations(1)
                .measurementIterations(1)
                .mode(Mode.Throughput)
                .build();
        new Runner(options).run();
    }
}
