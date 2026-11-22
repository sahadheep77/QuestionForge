package com.questionforge.context;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Default mock implementation of ContextBuilder for tests.
 */
@Component
public class MockContextBuilder implements ContextBuilder {

    @Override
    public ContextBundle buildContext(String targetMethodId, int depth) {
        if ("com.example.service.OrderService#processOrder(Long)".equals(targetMethodId)) {
            MethodNode target = new MethodNode(
                "com.example.service.OrderService#processOrder(Long)",
                "public Order processOrder(Long orderId)",
                "public Order processOrder(Long orderId) {\n" +
                "    Order order = orderRepository.findById(orderId);\n" +
                "    if (order == null) {\n" +
                "        throw new OrderNotFoundException(\"Order not found: \" + orderId);\n" +
                "    }\n" +
                "    paymentService.charge(orderId, order.getAmount());\n" +
                "    order.setStatus(OrderStatus.PROCESSED);\n" +
                "    return orderRepository.save(order);\n" +
                "}",
                false,
                0,
                "TARGET"
            );

            EnclosingClass clazz = new EnclosingClass(
                "com.example.service.OrderService",
                "OrderService",
                List.of("private final OrderRepository orderRepository;", "private final PaymentService paymentService;"),
                List.of("public Order processOrder(Long orderId)", "public void cancelOrder(Long orderId)"),
                "package com.example.service;\n\npublic class OrderService {}"
            );

            MethodNode caller = new MethodNode(
                "com.example.controller.OrderController#checkout(Long)",
                "public ResponseEntity<Order> checkout(Long id)",
                "public ResponseEntity<Order> checkout(Long id) { return ResponseEntity.ok(orderService.processOrder(id)); }",
                false,
                1,
                "CALLER"
            );

            MethodNode callee1 = new MethodNode(
                "com.example.service.PaymentService#charge(Long,BigDecimal)",
                "public void charge(Long id, BigDecimal amount)",
                "public void charge(Long id, BigDecimal amount) { /* charge logic */ }",
                false,
                1,
                "CALLEE"
            );

            MethodNode callee2 = new MethodNode(
                "com.example.repository.OrderRepository#findById(Long)",
                "public Order findById(Long id)",
                null, // signatureOnly
                true,
                1,
                "CALLEE"
            );

            DroppedItem dropped = new DroppedItem(
                "com.example.notification.EmailService#sendConfirmation(Long)",
                "DROPPED_SIZE_LIMIT",
                2
            );

            return new ContextBundle(
                target,
                clazz,
                List.of(caller),
                List.of(callee1, callee2),
                Map.of("filePath", "src/main/java/com/example/service/OrderService.java"),
                false,
                List.of(dropped),
                Collections.emptyList()
            );
        }

        return null; // Unknown target returns null -> maps to 404
    }
}
