package com.medops.messaging.infrastructure;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "medops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@SuppressWarnings("unused") // Spring invokes @Bean factory methods through the application context.
public class MessagingConfiguration {

    @Bean
    NewTopic appointmentsTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.appointmentsTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic reportsTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.reportsTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic appointmentsDeadLetterTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.appointmentsTopic() + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic reportsDeadLetterTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.reportsTopic() + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    ProducerFactory<String, DomainEventMessage> domainEventProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, DomainEventMessage> domainEventKafkaTemplate(
            ProducerFactory<String, DomainEventMessage> domainEventProducerFactory) {
        return new KafkaTemplate<>(domainEventProducerFactory);
    }

    @Bean
    DefaultErrorHandler domainEventErrorHandler(
            KafkaTemplate<String, DomainEventMessage> kafkaTemplate,
            MessagingProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, exception) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLT", Math.floorMod(record.partition(), 3)));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(
                properties.consumerRetryBackoffMs(), Math.max(0, properties.consumerRetryAttempts() - 1L)));
    }

    @Bean
    ConsumerFactory<String, DomainEventMessage> domainEventConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.medops.messaging");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JacksonJsonDeserializer<>(DomainEventMessage.class));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, DomainEventMessage> domainEventKafkaListenerContainerFactory(
            ConsumerFactory<String, DomainEventMessage> domainEventConsumerFactory,
            DefaultErrorHandler domainEventErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, DomainEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(domainEventConsumerFactory);
        factory.setCommonErrorHandler(domainEventErrorHandler);
        return factory;
    }
}
