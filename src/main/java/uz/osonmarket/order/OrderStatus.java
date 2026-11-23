package uz.osonmarket.order;

import java.util.List;

public enum OrderStatus {
    PENDING, NEW_UNPAID, NEW_PAID, CONFIRMED, PROCESSING, READY_FOR_PICKUP, SHIPPED,
    READY_FOR_DELIVERY, OUT_FOR_DELIVERY, DELIVERY_FAILED, PICKED_UP, DELIVERED, CANCELLED;

    static List<OrderStatus> next(String current, FulfillmentMethod method) {
        return switch (current == null ? "" : current) {
            case "PENDING", "NEW_UNPAID" -> List.of(NEW_PAID, CANCELLED);
            case "NEW_PAID" -> List.of(PROCESSING, CANCELLED);
            case "CONFIRMED" -> List.of(PROCESSING, CANCELLED);
            case "PROCESSING" -> method == FulfillmentMethod.PICKUP
                    ? List.of(READY_FOR_PICKUP, CANCELLED) : List.of(READY_FOR_DELIVERY, CANCELLED);
            case "READY_FOR_PICKUP" -> List.of(PICKED_UP, CANCELLED);
            case "SHIPPED", "READY_FOR_DELIVERY" -> List.of(OUT_FOR_DELIVERY, CANCELLED);
            case "OUT_FOR_DELIVERY" -> List.of(DELIVERED, DELIVERY_FAILED);
            case "DELIVERY_FAILED" -> List.of(OUT_FOR_DELIVERY, CANCELLED);
            default -> List.of();
        };
    }
}
