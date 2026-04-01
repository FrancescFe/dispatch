package org.francescfe.dispatch.service;

import org.francescfe.dispatch.client.StockServiceClient;
import org.francescfe.dispatch.message.DispatchCompleted;
import org.francescfe.dispatch.message.DispatchPreparing;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    private DispatchService service;
    @Mock
    private KafkaTemplate<String, Object> kafkaProducerMock;

    @Mock
    private StockServiceClient stockServiceClientMock;

    @BeforeEach
    void setUp() {
        service = new DispatchService(kafkaProducerMock, stockServiceClientMock);
    }

    @Test
    void process_Success() throws Exception {
        CompletableFuture<SendResult<String, Object>> sendFutureMock = mock(CompletableFuture.class);
        when(kafkaProducerMock.send(anyString(),anyString(), any())).thenReturn(sendFutureMock);
        when(stockServiceClientMock.checkAvailability(anyString())).thenReturn("true");

        String key = randomUUID().toString();
        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());
        service.process(key, testEvent);

        ArgumentCaptor<OrderDispatched> orderDispatchedCaptor = ArgumentCaptor.forClass(OrderDispatched.class);
        verify(kafkaProducerMock, times(1)).send(eq("order.dispatched"), eq(key), orderDispatchedCaptor.capture());

        ArgumentCaptor<DispatchPreparing> dispatchTrackingCaptor = ArgumentCaptor.forClass(DispatchPreparing.class);
        verify(kafkaProducerMock, times(1)).send(eq("dispatch.tracking"), eq(key), dispatchTrackingCaptor.capture());

        ArgumentCaptor<DispatchCompleted> dispatchCompletedCaptor = ArgumentCaptor.forClass(DispatchCompleted.class);
        verify(kafkaProducerMock, times(1)).send(eq("dispatch.tracking"), eq(key), dispatchCompletedCaptor.capture());

        OrderDispatched orderDispatched = orderDispatchedCaptor.getValue();
        DispatchPreparing dispatchPreparing = dispatchTrackingCaptor.getValue();
        DispatchCompleted dispatchCompleted = dispatchCompletedCaptor.getValue();

        verify(stockServiceClientMock, times(1)).checkAvailability(testEvent.item());
        assertEquals(testEvent.orderId(), orderDispatched.orderId());
        assertEquals("Dispatched: " + testEvent.item(), orderDispatched.notes());
        assertEquals(testEvent.orderId(), dispatchPreparing.orderId());
        assertEquals(testEvent.orderId(), dispatchCompleted.orderId());
        assertFalse(dispatchCompleted.date().isBlank());
    }

    @Test
    public void process_ProducerThrowsException() {
        String key = randomUUID().toString();
        CompletableFuture<SendResult<String, Object>> trackingSendFutureMock = mock(CompletableFuture.class);
        when(kafkaProducerMock.send(eq("dispatch.tracking"), eq(key), any())).thenReturn(trackingSendFutureMock);
        when(stockServiceClientMock.checkAvailability(anyString())).thenReturn("true");

        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());
        doThrow(new RuntimeException("Producer failure")).when(kafkaProducerMock).send(eq("order.dispatched"), eq(key), any(OrderDispatched.class));

        Exception exception = assertThrows(RuntimeException.class, () -> service.process(key, testEvent));

        verify(stockServiceClientMock, times(1)).checkAvailability(testEvent.item());
        verify(kafkaProducerMock, times(1)).send(eq("dispatch.tracking"), eq(key), any(DispatchPreparing.class));
        verify(kafkaProducerMock, never()).send(eq("dispatch.tracking"), eq(key), any(DispatchCompleted.class));
        verify(kafkaProducerMock, times(1)).send(eq("order.dispatched"), eq(key), any(OrderDispatched.class));
        assertEquals("Producer failure", exception.getMessage());
    }

    @Test
    void process_TrackingProducerThrowsException() {
        String key = randomUUID().toString();
        when(stockServiceClientMock.checkAvailability(anyString())).thenReturn("true");
        doThrow(new RuntimeException("Tracking producer failure"))
                .when(kafkaProducerMock).send(eq("dispatch.tracking"), anyString(), any(DispatchPreparing.class));

        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());

        Exception exception = assertThrows(RuntimeException.class, () -> service.process(key, testEvent));

        verify(stockServiceClientMock, times(1)).checkAvailability(testEvent.item());
        verify(kafkaProducerMock, times(1)).send(eq("dispatch.tracking"), eq(key), any(DispatchPreparing.class));
        verify(kafkaProducerMock, never()).send(eq("order.dispatched"), eq(key), any(OrderDispatched.class));
        assertEquals("Tracking producer failure", exception.getMessage());
    }

    @Test
    void process_CompletedTrackingProducerThrowsException() {
        String key = randomUUID().toString();
        CompletableFuture<SendResult<String, Object>> sendFutureMock = mock(CompletableFuture.class);
        when(kafkaProducerMock.send(eq("dispatch.tracking"), eq(key), any(DispatchPreparing.class))).thenReturn(sendFutureMock);
        when(kafkaProducerMock.send(eq("order.dispatched"), eq(key), any(OrderDispatched.class))).thenReturn(sendFutureMock);
        when(stockServiceClientMock.checkAvailability(anyString())).thenReturn("true");
        doThrow(new RuntimeException("Tracking completed producer failure"))
                .when(kafkaProducerMock).send(eq("dispatch.tracking"), eq(key), any(DispatchCompleted.class));

        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());

        Exception exception = assertThrows(RuntimeException.class, () -> service.process(key, testEvent));

        verify(stockServiceClientMock, times(1)).checkAvailability(testEvent.item());
        verify(kafkaProducerMock, times(1)).send(eq("dispatch.tracking"), eq(key), any(DispatchPreparing.class));
        verify(kafkaProducerMock, times(1)).send(eq("order.dispatched"), eq(key), any(OrderDispatched.class));
        verify(kafkaProducerMock, times(1)).send(eq("dispatch.tracking"), eq(key), any(DispatchCompleted.class));
        assertEquals("Tracking completed producer failure", exception.getMessage());
    }
}
