package com.omnia.benchmark;

public record BenchmarkResult(
        String clientType,
        int indexCount,
        int docCountPerIndex,
        String status,
        String details,
        double meanTimeMillis
) {
    public String toCsvRow() {
        return String.join(",",
                clientType,
                String.valueOf(indexCount),
                String.valueOf(docCountPerIndex),
                status,
                "\"" + details.replace("\"", "'") + "\"", // Escape quotes for CSV
                String.format("%.4f", meanTimeMillis)
        );
    }

    public static String getCsvHeader() {
        return "ClientType,IndexCount,DocCountPerIndex,Status,Details,MeanTime(ms)";
    }
}
