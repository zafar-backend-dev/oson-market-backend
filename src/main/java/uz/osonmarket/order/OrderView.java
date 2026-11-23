package uz.osonmarket.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(UUID id, String orderNumber, String status, String currency, String phone,
                        String telegramAddress, Instant placedAt, BigDecimal subtotal,
                        BigDecimal discountTotal, BigDecimal shippingTotal, BigDecimal grandTotal, List<Item> items,
                        FulfillmentMethod fulfillmentMethod, UUID branchId, String deliveryAddress,
                        BigDecimal latitude, BigDecimal longitude, BigDecimal distanceKm, BigDecimal pricePerKm,
                        String cancellationReason, Instant paidAt, boolean canCancel, boolean canRate,
                        Integer serviceRating, String serviceReview, Instant ratedAt) {
    public record Item(UUID variantId, String sku, String title, int quantity,
                       BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal lineTotal) {
    }
}
