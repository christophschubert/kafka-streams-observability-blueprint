package net.christophschubert.kafka.streams;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

public class WordCountDemo {

    private static final Logger log = LoggerFactory.getLogger(WordCountDemo.class);

    public static final String HEALTH_CHECK_PORT_CONFIG = "health.check.port";
    public static final String HEALTH_CHECK_PORT_DEFAULT = "7171";

    private final HttpServer server;
    private final KafkaStreams kafkaStreams;
    private final Topology topology;

    public WordCountDemo(Properties config) {
        final var healthCheckPort = Integer.parseInt(config.getProperty(HEALTH_CHECK_PORT_CONFIG, HEALTH_CHECK_PORT_DEFAULT));
        this.server = setupHealthChecks(healthCheckPort);
        this.topology = buildTopology();
        this.kafkaStreams = new KafkaStreams(this.topology, config);
        kafkaStreams.start();
    }

    private void sendStringResponse(HttpExchange httpExchange, int resultCode, Object body) {
        final var method = httpExchange.getRequestMethod();
        final var uri = httpExchange.getRequestURI();
        try {
            final var response = Objects.toString(body).getBytes(StandardCharsets.UTF_8);

            final var responseSize = response.length;
            httpExchange.sendResponseHeaders(resultCode, responseSize);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(response);
            }
            log.info("Handled {} request for '{}' with status {}, send {} bytes.",
                    method,
                    uri,
                    resultCode,
                    responseSize
            );
        } catch (IOException e) {
            log.error("Error handling {} request for '{}'", method, uri, e);
        }
    }

    HttpServer setupHealthChecks(int healthCheckPort) {
        try {
            final var server = HttpServer.create(new InetSocketAddress(healthCheckPort), 0);

            server.createContext("/topology", httpExchange -> {
                if (topology == null) {
                    sendStringResponse(httpExchange, 500, "Topology has not been created");
                } else {
                    sendStringResponse(httpExchange, 200, topology.describe());
                }
            });

            server.createContext("/health", httpExchange -> {
                if (kafkaStreams == null) {
                    sendStringResponse(httpExchange, 500, "KafkaStreams instance has not been created");
                } else {
                    final var streamsState = kafkaStreams.state();
                    final int returnCode = streamsState.isRunningOrRebalancing() ? 200 : 400;
                    sendStringResponse(httpExchange, returnCode, streamsState);
                }
            });

            new Thread(server::start).start();
            log.info("Listening for health checks on port {}", healthCheckPort);
            return server;
        } catch (IOException e) {
            throw new RuntimeException("Could not create HTTP server", e);
        }
    }

    void shutDown() {
        final var delayInSeconds = 5;//double-check: is this reasonable (for both resources)?
        kafkaStreams.close(Duration.ofSeconds(delayInSeconds));
        server.stop(delayInSeconds);
    }


    Topology buildTopology() {
        final var builder = new StreamsBuilder();
        final KStream<String, String> inputTopic = builder.stream("input");
        inputTopic.flatMapValues(l -> Arrays.asList(l.split(" ")))
                .groupBy((_k, v) -> v)
                .count()
                .toStream()
                .to("output", Produced.valueSerde(Serdes.Long()));
        return builder.build();
    }

    public static void main(String... args) {
        final var config = new Properties();

        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "word-count-app");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());

        final var app = new WordCountDemo(config);

        Runtime.getRuntime().addShutdownHook(new Thread(app::shutDown));
        log.info("Kafka Streams app started");
    }
}
