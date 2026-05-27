package com.fbp.engine.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteOptions;
import com.influxdb.client.write.Point;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class InfluxBatchWriter {
    private final InfluxDBClient client;
    private final WriteApi writeApi;
    private final String bucket;
    private final String organization;

    public InfluxBatchWriter(InfluxConfig config) {
        this.client = InfluxDBClientFactory.create(config.getUrl(), 
                config.getToken() != null ? config.getToken().toCharArray() : new char[0]);
        this.organization = config.getOrg();
        this.bucket = config.getBucket();

        WriteOptions options = WriteOptions.builder()
                .batchSize(config.getBatch().getSize())
                .flushInterval(config.getBatch().getFlushIntervalMs())
                .bufferLimit(config.getBuffer().getMaxSize())
                .retryInterval(config.getRetry().getInitialBackoffMs())
                .build();
        
        this.writeApi = this.client.makeWriteApi(options);
    }

    public void writePoint(Point point) {
        if (point != null) {
            writeApi.writePoint(bucket, organization, point);
        }
    }

    public void writePoints(List<Point> points) {
        if (points != null && !points.isEmpty()) {
            writeApi.writePoints(bucket, organization, points);
        }
    }

    public void close() {
        writeApi.close();
        client.close();
    }
}
