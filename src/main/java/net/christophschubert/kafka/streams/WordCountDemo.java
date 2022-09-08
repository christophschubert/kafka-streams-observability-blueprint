package net.christophschubert.kafka.streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Properties;

public class WordCountDemo {

    private static final Logger log = LoggerFactory.getLogger(WordCountDemo.class);

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

        final var app = new WordCountDemo();

        final var topology = app.buildTopology();
        final var kafkaStreams = new KafkaStreams(topology, config);
        kafkaStreams.start();
        log.info("Kafka Streams app started");
    }
}
