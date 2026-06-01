package com.phs.application.service.impl;

import com.phs.application.entity.CartItem;
import com.phs.application.entity.Order;
import com.phs.application.entity.Product;
import com.phs.application.entity.ProductSize;
import com.phs.application.entity.Promotion;
import com.phs.application.entity.User;
import com.phs.application.exception.BadRequestException;
import com.phs.application.exception.NotFoundException;
import com.phs.application.model.dto.CartItemDTO;
import com.phs.application.model.request.AddCartItemRequest;
import com.phs.application.model.request.CheckoutCartRequest;
import com.phs.application.model.request.UpdateCartItemRequest;
import com.phs.application.repository.CartItemRepository;
import com.phs.application.repository.OrderRepository;
import com.phs.application.repository.ProductRepository;
import com.phs.application.repository.ProductSizeRepository;
import com.phs.application.service.CartService;
import com.phs.application.service.PromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.phs.application.config.Contant.ORDER_STATUS;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSizeRepository productSizeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PromotionService promotionService;

    @Override
    public List<CartItemDTO> getCartItems(long userId) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<CartItemDTO> dtos = new ArrayList<>();
        for (CartItem item : items) {
            dtos.add(toDTO(item));
        }
        return dtos;
    }

    @Override
    public long getTotalAmount(long userId) {
        long total = 0;
        for (CartItem item : cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            total += unitPrice(item.getProduct()) * item.getQuantity();
        }
        return total;
    }

    @Override
    public long getItemCount(long userId) {
        return cartItemRepository.countByUserId(userId);
    }

    @Override
    @Transactional
    public CartItemDTO addItem(long userId, AddCartItemRequest request) {
        Optional<Product> productOpt = productRepository.findById(request.getProductId());
        if (productOpt.isEmpty()) {
            throw new NotFoundException("Sản phẩm không tồn tại");
        }
        ProductSize productSize = productSizeRepository.checkProductAndSizeAvailable(request.getProductId(), request.getSize());
        if (productSize == null) {
            throw new BadRequestException("Size giày này đã hết hàng");
        }

        Optional<CartItem> existing = cartItemRepository.findByUserAndProductAndSize(userId, request.getProductId(), request.getSize());
        CartItem item;
        if (existing.isPresent()) {
            item = existing.get();
            int newQty = item.getQuantity() + request.getQuantity();
            if (newQty > productSize.getQuantity()) {
                throw new BadRequestException("Số lượng vượt quá tồn kho (" + productSize.getQuantity() + ")");
            }
            item.setQuantity(newQty);
            item.setModifiedAt(new Timestamp(System.currentTimeMillis()));
        } else {
            if (request.getQuantity() > productSize.getQuantity()) {
                throw new BadRequestException("Số lượng vượt quá tồn kho (" + productSize.getQuantity() + ")");
            }
            User user = new User();
            user.setId(userId);
            item = new CartItem();
            item.setUser(user);
            item.setProduct(productOpt.get());
            item.setSize(request.getSize());
            item.setQuantity(request.getQuantity());
            item.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        }
        cartItemRepository.save(item);
        return toDTO(item);
    }

    @Override
    @Transactional
    public CartItemDTO updateItem(long userId, long itemId, UpdateCartItemRequest request) {
        CartItem item = loadOwnedItem(userId, itemId);
        ProductSize productSize = productSizeRepository.checkProductAndSizeAvailable(item.getProduct().getId(), item.getSize());
        if (productSize == null) {
            throw new BadRequestException("Sản phẩm đã hết hàng");
        }
        if (request.getQuantity() > productSize.getQuantity()) {
            throw new BadRequestException("Số lượng vượt quá tồn kho (" + productSize.getQuantity() + ")");
        }
        item.setQuantity(request.getQuantity());
        item.setModifiedAt(new Timestamp(System.currentTimeMillis()));
        cartItemRepository.save(item);
        return toDTO(item);
    }

    @Override
    @Transactional
    public void removeItem(long userId, long itemId) {
        CartItem item = loadOwnedItem(userId, itemId);
        cartItemRepository.delete(item);
    }

    @Override
    @Transactional
    public void clearCart(long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    @Override
    @Transactional
    public List<Order> checkout(long userId, CheckoutCartRequest request) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (items.isEmpty()) {
            throw new BadRequestException("Giỏ hàng trống");
        }

        // Validate ton kho truoc khi tao order
        for (CartItem item : items) {
            ProductSize ps = productSizeRepository.checkProductAndSizeAvailable(item.getProduct().getId(), item.getSize());
            if (ps == null || ps.getQuantity() < item.getQuantity()) {
                throw new BadRequestException("Sản phẩm \"" + item.getProduct().getName() + "\" size " + item.getSize() + " không đủ tồn kho");
            }
        }

        List<Order> createdOrders = new ArrayList<>();
        User buyer = new User();
        buyer.setId(userId);

        // ===== Tinh tong tien + tong so luong + ap dung coupon (neu co) =====
        long cartSubTotal = 0L;
        long totalQty = 0L;
        for (CartItem item : items) {
            cartSubTotal += unitPrice(item.getProduct()) * item.getQuantity();
            totalQty += item.getQuantity();
        }

        // Kiem tra coupon, tinh tong tien duoc giam (PER-UNIT cho fixed type)
        long totalDiscount = 0L;
        Promotion appliedPromotion = null;
        String couponCode = request.getCouponCode();
        if (couponCode != null && !couponCode.trim().isEmpty()) {
            try {
                appliedPromotion = promotionService.checkPromotion(couponCode.trim());
            } catch (Exception e) {
                throw new BadRequestException("Mã khuyến mãi không hợp lệ");
            }
            if (appliedPromotion == null) {
                throw new BadRequestException("Mã khuyến mãi không tồn tại hoặc đã hết hạn");
            }
            if (appliedPromotion.getDiscountType() == 1) {
                // % giam: ap dung tren tong subTotal
                totalDiscount = cartSubTotal * appliedPromotion.getDiscountValue() / 100;
                if (appliedPromotion.getMaximumDiscountValue() > 0
                        && totalDiscount > appliedPromotion.getMaximumDiscountValue()) {
                    totalDiscount = appliedPromotion.getMaximumDiscountValue();
                }
            } else {
                // Giam co dinh: PER-UNIT — 1 doi = -X, 2 doi = -2X
                totalDiscount = (long) appliedPromotion.getDiscountValue() * totalQty;
                if (appliedPromotion.getMaximumDiscountValue() > 0
                        && totalDiscount > appliedPromotion.getMaximumDiscountValue()) {
                    totalDiscount = appliedPromotion.getMaximumDiscountValue();
                }
                if (totalDiscount > cartSubTotal) totalDiscount = cartSubTotal;
            }
        }

        // Cung 1 timestamp cho tat ca orders trong cung 1 lan checkout
        Timestamp checkoutTime = new Timestamp(System.currentTimeMillis());

        long discountUsed = 0L;
        for (int idx = 0; idx < items.size(); idx++) {
            CartItem item = items.get(idx);
            long unit = unitPrice(item.getProduct());
            long itemTotal = unit * item.getQuantity();

            // Phan bo discount theo ti le itemTotal/cartSubTotal
            long itemDiscount;
            if (idx == items.size() - 1) {
                // Item cuoi nhan phan con lai de tranh sai so do lam tron
                itemDiscount = totalDiscount - discountUsed;
            } else {
                itemDiscount = (cartSubTotal > 0)
                        ? (totalDiscount * itemTotal / cartSubTotal)
                        : 0;
                discountUsed += itemDiscount;
            }
            if (itemDiscount < 0) itemDiscount = 0;
            if (itemDiscount > itemTotal) itemDiscount = itemTotal;

            Order order = new Order();
            order.setBuyer(buyer);
            order.setCreatedBy(buyer);
            order.setProduct(item.getProduct());
            order.setSize(item.getSize());
            order.setQuantity(item.getQuantity());
            order.setReceiverName(request.getReceiverName());
            order.setReceiverPhone(request.getReceiverPhone());
            order.setReceiverAddress(request.getReceiverAddress());
            order.setNote(request.getNote());
            order.setPrice(itemTotal);
            // Convention: totalPrice = so tien giam (discount)
            order.setTotalPrice(itemDiscount);
            order.setStatus(ORDER_STATUS);
            order.setCreatedAt(checkoutTime);
            if (appliedPromotion != null && itemDiscount > 0) {
                Order.UsedPromotion used = new Order.UsedPromotion(
                        appliedPromotion.getCouponCode(),
                        appliedPromotion.getDiscountType(),
                        appliedPromotion.getDiscountValue(),
                        appliedPromotion.getMaximumDiscountValue()
                );
                order.setPromotion(used);
            }
            orderRepository.save(order);

            // Tru ton kho
            ProductSize ps = productSizeRepository.checkProductAndSizeAvailable(item.getProduct().getId(), item.getSize());
            ps.setQuantity(ps.getQuantity() - item.getQuantity());
            productSizeRepository.save(ps);

            createdOrders.add(order);
        }

        return createdOrders;
    }

    private CartItem loadOwnedItem(long userId, long itemId) {
        Optional<CartItem> opt = cartItemRepository.findById(itemId);
        if (opt.isEmpty()) {
            throw new NotFoundException("Mục giỏ hàng không tồn tại");
        }
        CartItem item = opt.get();
        if (item.getUser().getId() != userId) {
            throw new BadRequestException("Bạn không có quyền thao tác trên mục này");
        }
        return item;
    }

    private long unitPrice(Product product) {
        return product.getSalePrice() > 0 ? product.getSalePrice() : product.getPrice();
    }

    private CartItemDTO toDTO(CartItem item) {
        Product product = item.getProduct();
        ProductSize ps = productSizeRepository.checkProductAndSizeAvailable(product.getId(), item.getSize());
        long unit = unitPrice(product);
        String image = (product.getImages() != null && !product.getImages().isEmpty()) ? product.getImages().get(0) : "";

        CartItemDTO dto = new CartItemDTO();
        dto.setId(item.getId());
        dto.setProductId(product.getId());
        dto.setProductName(product.getName());
        dto.setProductImage(image);
        dto.setProductSlug(product.getSlug());
        dto.setPrice(product.getPrice());
        dto.setSalePrice(product.getSalePrice());
        dto.setSize(item.getSize());
        dto.setQuantity(item.getQuantity());
        dto.setSubTotal(unit * item.getQuantity());
        dto.setInStock(ps != null && ps.getQuantity() >= item.getQuantity());
        return dto;
    }
}
