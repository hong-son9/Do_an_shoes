package com.phs.application.service;

import com.phs.application.entity.Order;
import com.phs.application.model.dto.CartItemDTO;
import com.phs.application.model.request.AddCartItemRequest;
import com.phs.application.model.request.CheckoutCartRequest;
import com.phs.application.model.request.UpdateCartItemRequest;

import java.util.List;

public interface CartService {

    List<CartItemDTO> getCartItems(long userId);

    long getTotalAmount(long userId);

    long getItemCount(long userId);

    CartItemDTO addItem(long userId, AddCartItemRequest request);

    CartItemDTO updateItem(long userId, long itemId, UpdateCartItemRequest request);

    void removeItem(long userId, long itemId);

    void clearCart(long userId);

    List<Order> checkout(long userId, CheckoutCartRequest request);
}
