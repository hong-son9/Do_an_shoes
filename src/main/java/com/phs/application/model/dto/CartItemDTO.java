package com.phs.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class CartItemDTO {
    private long id;
    private String productId;
    private String productName;
    private String productImage;
    private String productSlug;
    private long price;
    private long salePrice;
    private int size;
    private int quantity;
    private long subTotal;
    private boolean inStock;
}
