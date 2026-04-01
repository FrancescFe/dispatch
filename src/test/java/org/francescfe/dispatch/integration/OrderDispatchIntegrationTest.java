package org.francescfe.dispatch.integration;

import org.francescfe.dispatch.DispatchConfiguration;
import org.francescfe.dispatch.client.StockServiceClient;
import org.francescfe.dispatch.handler.OrderCreatedHandler;
import org.francescfe.dispatch.message.DispatchCompleted;
import org.francescfe.dispatch.message.DispatchPreparing;
import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.message.OrderDispatched;
import org.francescfe.dispatch.service.DispatchService;
import org.francescfe.dispatch.util.TestEventData;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;
import static org.francescfe.dispatch.integration.WiremockUtils.stubWiremock;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {
        DispatchConfiguration.class,
        OrderDispatchIntegrationTest.TestConfig.class,
        StockServiceClient.class,
        DispatchService.class,
        OrderCreatedHandler.class,
})
@EnableWireMock(@ConfigureWireMock())
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@EmbeddedKafka(controlledShutdown = true)
public class OrderDispatchIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(OrderDispatchIntegrationTest.class);
    private final static String ORDER_CREATED_TOPIC = "order.created";
    private final static String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    private final static String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";
    private final static String ORDER_CREATED_DLT_TOPIC = "order.created-dlt";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private KafkaTestListener testListener;

    @TestConfiguration
    @EnableKafka
    static class TestConfig {
        @Bean
        public KafkaTestListener testListener() {
            return new KafkaTestListener();
        }
    }

    @KafkaListener(
            id = "dispatchTrackingTestListener",
            groupId = "KafkaIntegrationTest",
            topics = {DISPATCH_TRACKING_TOPIC, ORDER_DISPATCHED_TOPIC, ORDER_CREATED_DLT_TOPIC},
            containerFactory = "kafkaListenerContainerFactory"
    )
    public static class KafkaTestListener {
        AtomicInteger dispatchPreparingCounter = new AtomicInteger(0);
        AtomicInteger dispatchCompletedCounter = new AtomicInteger(0);
        AtomicInteger orderDispatchedCounter = new AtomicInteger(0);
        AtomicInteger orderCreatedDLTCounter = new AtomicInteger(0);

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

        @KafkaHandler
        void receiveOrderDispatched(@Header(KafkaHeaders.RECEIVED_KEY) String key, @Payload OrderDispatched payload) {
            log.debug("Received OrderDispatched key: {} - payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            orderDispatchedCounter.incrementAndGet();
        }

        @KafkaHandler
        void receiveOrderCreatedDLT(@Header(KafkaHeaders.RECEIVED_KEY) String key, @Payload OrderCreated payload) {
            log.debug("Received OrderCreated DLT key: {} - payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            orderCreatedDLTCounter.incrementAndGet();
        }
    }

    @BeforeEach
    public void setup() {
        EmbeddedKafkaBroker embeddedKafkaBroker = applicationContext.getBean(EmbeddedKafkaBroker.class);
        KafkaListenerEndpointRegistry registry = applicationContext.getBean(KafkaListenerEndpointRegistry.class);

        testListener.dispatchPreparingCounter.set(0);
        testListener.dispatchCompletedCounter.set(0);
        testListener.orderDispatchedCounter.set(0);
        testListener.orderCreatedDLTCounter.set(0);

        WiremockUtils.reset();

        registry.getListenerContainers().forEach(
                container -> ContainerTestUtils.waitForAssignment(container,
                        Objects.requireNonNull(container.getContainerProperties().getTopics()).length * embeddedKafkaBroker.getPartitionsPerTopic()
        ));
    }

    @Test
    public void testOrderDispatchFlow_Success() throws Exception {
        stubWiremock("/api/stock?item=my-item", 200, "true");

        OrderCreated orderCreated = TestEventData.buildOrderCreated(randomUUID(), "my-item");
        String key = randomUUID().toString();
        kafkaTemplate.send(ORDER_CREATED_TOPIC, key, orderCreated).get();

        await().atMost(3, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.dispatchPreparingCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.dispatchCompletedCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.orderDispatchedCounter::get, equalTo(1));
        assertEquals(0, testListener.orderCreatedDLTCounter.get());
    }

    @Test
    public void testOrderDispatchFlow_NonRetryableException() throws Exception {
        stubWiremock("/api/stock?item=my-item", 400, "Bad Request");

        OrderCreated orderCreated = TestEventData.buildOrderCreated(randomUUID(), "my-item");
        String key = randomUUID().toString();
        kafkaTemplate.send(ORDER_CREATED_TOPIC, key, orderCreated).get();

        await().atMost(3, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.orderCreatedDLTCounter::get, equalTo(1));
        assertEquals(0, testListener.dispatchPreparingCounter.get());
        assertEquals(0, testListener.orderDispatchedCounter.get());
        assertEquals(0, testListener.dispatchCompletedCounter.get());
    }

    @Test
    public void testOrderDispatchFlow_RetryThenSuccess() throws Exception {
        stubWiremock("/api/stock?item=my-item", 503, "Service unavailable", "failOnce", STARTED, "succeedNextTime");
        stubWiremock("/api/stock?item=my-item", 200, "true", "failOnce", "succeedNextTime", "succeedNextTime");

        OrderCreated orderCreated = TestEventData.buildOrderCreated(randomUUID(), "my-item");
        String key = randomUUID().toString();
        kafkaTemplate.send(ORDER_CREATED_TOPIC, key, orderCreated).get();

        await().atMost(3, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.dispatchPreparingCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.dispatchCompletedCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.orderDispatchedCounter::get, equalTo(1));
        assertEquals(0, testListener.orderCreatedDLTCounter.get());
    }

    @Test
    public void testOrderDispatchFlow_RetryUntilFailure() throws Exception {
        stubWiremock("/api/stock?item=my-item", 503, "Service unavailable");

        OrderCreated orderCreated = TestEventData.buildOrderCreated(randomUUID(), "my-item");
        String key = randomUUID().toString();
        kafkaTemplate.send(ORDER_CREATED_TOPIC, key, orderCreated).get();

        await().atMost(5, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testListener.orderCreatedDLTCounter::get, equalTo(1));
        assertEquals(0, testListener.dispatchPreparingCounter.get());
        assertEquals(0, testListener.orderDispatchedCounter.get());
        assertEquals(0, testListener.dispatchCompletedCounter.get());
    }
}
