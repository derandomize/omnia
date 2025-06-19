package com.omnia.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(OmniaBenchmark.class.getSimpleName())
                .build();

        Collection<RunResult> runResults = new Runner(opt).run();

        List<BenchmarkResult> results = new ArrayList<>();
        for (RunResult runResult : runResults) {
            String clientType = runResult.getParams().getParam("clientType");
            int indexCount = Integer.parseInt(runResult.getParams().getParam("indexCount"));
            double score = runResult.getPrimaryResult().getScore();

            String status;
            String details;

            // JMH reports a score of 0.0 or NaN if the benchmark method wasn't executed,
            // which we use as a signal that setup failed.
            if (Double.isNaN(score) || score == 0.0) {
                status = "SETUP_FAILED";
                // In a real scenario, you'd need a way to pass the exception message here.
                // For now, we assume failure is due to index limits for standard client.
                if ("STANDARD".equals(clientType) && indexCount > 1000) {
                    details = "Likely failed due to OpenSearch index limit.";
                } else {
                    details = "Unknown setup error, check logs.";
                }
            } else {
                status = "SUCCESS";
                details = "Benchmark completed successfully.";
            }

            results.add(new BenchmarkResult(
                    clientType,
                    indexCount,
                    10, // Hardcoded doc count from benchmark class
                    status,
                    details,
                    score
            ));
        }

        writeReport(results);
    }

    private static void writeReport(List<BenchmarkResult> results) throws IOException {
        String fileName = "benchmark_report.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println(BenchmarkResult.getCsvHeader());
            results.forEach(result -> writer.println(result.toCsvRow()));
        }
        System.out.println("\nBenchmark report generated: " + fileName);
    }
}
