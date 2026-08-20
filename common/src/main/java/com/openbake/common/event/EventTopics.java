package com.openbake.common.event;

public final class EventTopics {

    public static final String PRODUCT_VIEWED = "product.viewed.v1";
    public static final String CART_ITEM_ADDED = "cart.item-added.v1";
    public static final String ORDER_PURCHASE_CONFIRMED = "order.purchase-confirmed.v1";
    public static final String MEMBER_WITHDRAWN = "member.withdrawn.v1";

    private EventTopics() {
    }

    public static InteractionType interactionTypeFor(String topic) {
        return switch (topic) {
            case PRODUCT_VIEWED -> InteractionType.VIEW;
            case CART_ITEM_ADDED -> InteractionType.CART_ADD;
            case ORDER_PURCHASE_CONFIRMED -> InteractionType.PURCHASE;
            default -> throw new IllegalArgumentException("unsupported interaction topic: " + topic);
        };
    }
}
