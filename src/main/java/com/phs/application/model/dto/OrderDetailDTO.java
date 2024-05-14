package com.phs.application.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailDTO {
    private Long id;

    private Long totalPrice;

    private Long productPrice;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private Integer status;
    private Integer quantity;

    private String statusText;

    private Integer sizeVn;

    private Double sizeUs;

    private Double sizeCm;

    private String productName;

    private String productImg;

    public OrderDetailDTO(Long id, Long totalPrice, Integer quantity, Long productPrice,
                          String receiverName, String receiverPhone, String receiverAddress,
                          Integer status,
                          Integer sizeVn,
                          String productName, String productImg) {
        this.id = id;
        this.totalPrice = totalPrice;
        this.quantity = quantity;
        this.productPrice = productPrice;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.receiverAddress = receiverAddress;
        this.sizeVn = sizeVn;
        this.status = status;
        this.productName = productName;
        this.productImg = productImg;
    }
}
