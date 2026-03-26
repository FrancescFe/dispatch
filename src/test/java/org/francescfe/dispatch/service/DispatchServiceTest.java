package org.francescfe.dispatch.service;

import org.francescfe.dispatch.message.DispatchTracking;
import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.message.OrderDispatched;
import org.francescfe.dispatch.util.TestEventData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CompletableFuture;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    private DispatchService service;
    @Mock
    private KafkaTemplate<String, Object> kafkaProducerMock;

    @BeforeEach
    void setUp() {
        service = new DispatchService(kafkaProducerMock);
    }

    @Test
    void process_Success() throws Exception {
        CompletableFuture<SendResult<String, Object>> sendFutureMock = mock(CompletableFuture.class);
        when(kafkaProducerMock.send(anyString(), any())).thenReturn(sendFutureMock);

        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());
        service.process(testEvent);

        verify(kafkaProducerMock, times(1)).send(eq("order.dispatched"), any(OrderDispatched.class));

        ArgumentCaptor<DispatchTracking> dispatchTrackingCaptor = ArgumentCaptor.forClass(DispatchTracking.class);
        verify(kafkaProducerMock, times(1)).send(eq("dispatch.tracking"), dispatchTrackingCaptor.capture());

        DispatchTracking dispatchTracking = dispatchTrackingCaptor.getValue();
        assertEquals(testEvent.orderId(), dispatchTracking.orderId());
        assertEquals("DISPATCHED", dispatchTracking.status());
    }

    @Test
    public void process_ProducerThrowsException() {
        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());
        doThrow(new RuntimeException("Producer failure")).when(kafkaProducerMock).send(eq("order.dispatched"), any(OrderDispatched.class));

        Exception exception = assertThrows(RuntimeException.class, () -> service.process(testEvent));

        verify(kafkaProducerMock, times(1)).send(eq("order.dispatched"), any(OrderDispatched.class));
        assertEquals("Producer failure", exception.getMessage());
    }

    @Test
    void process_TrackingProducerThrowsException() {
        CompletableFuture<SendResult<String, Object>> sendFutureMock = mock(CompletableFuture.class);
        when(kafkaProducerMock.send(eq("order.dispatched"), any(OrderDispatched.class))).thenReturn(sendFutureMock);
        doThrow(new RuntimeException("Tracking producer failure"))
                .when(kafkaProducerMock).send(eq("dispatch.tracking"), any(DispatchTracking.class));

        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());

        Exception exception = assertThrows(RuntimeException.class, () -> service.process(testEvent));

        verify(kafkaProducerMock, times(1)).send(eq("order.dispatched"), any(OrderDispatched.class));
        verify(kafkaProducerMock, times(1)).send(eq("dispatch.tracking"), any(DispatchTracking.class));
        assertEquals("Tracking producer failure", exception.getMessage());
    }
}
