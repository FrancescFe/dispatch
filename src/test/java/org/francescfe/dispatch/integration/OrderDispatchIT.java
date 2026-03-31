package org.francescfe.dispatch.integration;

import org.francescfe.dispatch.DispatchConfiguration;
import org.francescfe.dispatch.handler.OrderCreatedHandler;
import org.francescfe.dispatch.message.DispatchCompleted;
import org.francescfe.dispatch.message.DispatchPreparing;
import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.message.OrderDispatched;
import org.francescfe.dispatch.service.DispatchService;
import org.francescfe.dispatch.util.TestEventData;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {
        DispatchConfiguration.class,
        OrderDispatchIT.TestConfig.class,
        DispatchService.class,
        OrderCreatedHandler.class,
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@EmbeddedKafka(controlledShutdown = true)
public class OrderDispatchIT {

    private static final Logger log = LoggerFactory.getLogger(OrderDispatchIT.class);
    private final static String ORDER_CREATED_TOPIC = "order.created";
    private final static String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    private final static String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private KafkaTestListener testListener;

    @TestConfiguration
    @EnableKafka
    static class TestConfig {
        private static final String TRUSTED_PACKAGES = "org.francescfe.dispatch.message";
        private static final String ORDER_DISPATCHED_TYPE = "org.francescfe.dispatch.message.OrderDispatched";

        @Bean
        public KafkaTestListener testListener() {
            return new KafkaTestListener();
        }

        @Bean
        public ConsumerFactory<String, Object> dispatchTrackingConsumerFactory(EmbeddedKafkaBroker embeddedKafkaBroker) {
            Map<String, Object> config = new HashMap<>();
            config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
            config.put(ConsumerConfig.GROUP_ID_CONFIG, "KafkaIntegrationTest");
            config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
            config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
            config.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGES);
            config.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, true);
            return new DefaultKafkaConsumerFactory<>(config);
        }

        @Bean
        public ConcurrentKafkaListenerContainerFactory<String, Object> dispatchTrackingKafkaListenerContainerFactory(
                ConsumerFactory<String, Object> dispatchTrackingConsumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(dispatchTrackingConsumerFactory);
            return factory;
        }

        @Bean
        public ConsumerFactory<String, Object> orderDispatchedConsumerFactory(EmbeddedKafkaBroker embeddedKafkaBroker) {
            Map<String, Object> config = new HashMap<>();
            config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
            config.put(ConsumerConfig.GROUP_ID_CONFIG, "KafkaIntegrationTest");
            config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
            config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
            config.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGES);
            config.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, ORDER_DISPATCHED_TYPE);
            config.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaConsumerFactory<>(config);
        }

        @Bean
        public ConcurrentKafkaListenerContainerFactory<String, Object> orderDispatchedKafkaListenerContainerFactory(
                ConsumerFactory<String, Object> orderDispatchedConsumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(orderDispatchedConsumerFactory);
            return factory;
        }
    }

    @KafkaListener(
            id = "dispatchTrackingTestListener",
            groupId = "KafkaIntegrationTest",
            topics = DISPATCH_TRACKING_TOPIC,
            containerFactory = "dispatchTrackingKafkaListenerContainerFactory"
    )
    public static class KafkaTestListener {
        AtomicInteger dispatchPreparingCounter = new AtomicInteger(0);
        AtomicInteger dispatchCompletedCounter = new AtomicInteger(0);
        AtomicInteger orderDispatchedCounter = new AtomicInteger(0);

        @KafkaHandler
        void receiveDispatchPreparing(@Header(KafkaHeaders.RECEIVED_KEY) String key, @Payload DispatchPreparing payload) {
            log.debug("Received DispatchPreparing key: {} - payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            dispatchPreparingCounter.incrementAndGet();
        }

        @KafkaHandler
        void receiveDispatchCompleted(@Header(KafkaHeaders.RECEIVED_KEY) String key, @Payload DispatchCompleted payload) {
            log.debug("Received DispatchCompleted key: {} - payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            dispatchCompletedCounter.incrementAndGet();
        }

        @KafkaListener(
                groupId = "KafkaIntegrationTest",
                topics = ORDER_DISPATCHED_TOPIC,
                containerFactory = "orderDispatchedKafkaListenerContainerFactory"
        )
        void receiveOrderDispatched(@Header(KafkaHeaders.RECEIVED_KEY) String key, @Payload OrderDispatched payload) {
            log.debug("Received OrderDispatched key: {} - payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            orderDispatchedCounter.incrementAndGet();
        }
    }

    @BeforeEach
    public void setup() {
        EmbeddedKafkaBroker embeddedKafkaBroker = applicationContext.getBean(EmbeddedKafkaBroker.class);
        KafkaListenerEndpointRegistry registry = applicationContext.getBean(KafkaListenerEndpointRegistry.class);

        testListener.dispatchPreparingCounter.set(0);
        testListener.dispatchCompletedCounter.set(0);
        testListener.orderDispatchedCounter.set(0);

        registry.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(
                (ConcurrentMessageListenerContainer<?, ?>) container,
                container.getContainerProperties().getTopics().length * embeddedKafkaBroker.getPartitionsPerTopic()
        ));
    }

    @Test
    public void testOrderDispatchFlow() throws Exception {
        OrderCreated orderCreated = TestEventData.buildOrderCreated(randomUUID(), "my-item");
        String key = randomUUID().toString();
        kafkaTemplate.send(ORDER_CREATED_TOPIC, key, orderCreated).get();

        await().atMost(3, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.dispatchPreparingCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.dispatchCompletedCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.orderDispatchedCounter::get, equalTo(1));
    }

}
