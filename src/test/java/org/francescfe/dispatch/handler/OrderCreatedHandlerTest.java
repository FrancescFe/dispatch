package org.francescfe.dispatch.handler;

import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.service.DispatchService;
import org.francescfe.dispatch.util.TestEventData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OrderCreatedHandlerTest {

    private OrderCreatedHandler handler;
    private DispatchService dispatchServiceMock;

    @BeforeEach
    void setUp() {
        dispatchServiceMock = mock(DispatchService.class);
        handler = new OrderCreatedHandler(dispatchServiceMock);
    }

    @Test
    void listen_Success() throws Exception {
        String key = randomUUID().toString();
        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());
        handler.listen(0, key, testEvent);
        verify(dispatchServiceMock, times(1)).process(key, testEvent);
    }

    @Test
    public void listen_ServiceThrowsException() throws Exception {
        String key = randomUUID().toString();
        OrderCreated testEvent = TestEventData.buildOrderCreated(randomUUID(), randomUUID().toString());
        doThrow(new RuntimeException("Service failure")).when(dispatchServiceMock).process(key, testEvent);

        handler.listen(0, key, testEvent);

        verify(dispatchServiceMock, times(1)).process(key, testEvent);
    }
}