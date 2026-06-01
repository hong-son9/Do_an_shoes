package com.phs.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderInfoDTO {
    private long id;

    // Theo convention moi: totalPrice trong DB la SO TIEN GIAM (discount)
    private long totalPrice;

    // Tong gia san pham (= unit price * quantity)
    private long productPrice;

    private int quantity;
    private int sizeVn;

    private double sizeUs;

    private double sizeCm;

    private String productName;

    private String productImg;

    // Constructor cu — giu de tranh break code khac
    public OrderInfoDTO(long id, long totalPrice, int quantity, int sizeVn, String productName, String productImg) {
        this.id = id;
        this.totalPrice = totalPrice;
        this.quantity = quantity;
        this.sizeVn = sizeVn;
        this.productName = productName;
        this.productImg = productImg;
    }

    // Constructor moi — dung cho native query co them cot price
    public OrderInfoDTO(long id, long totalPrice, long productPrice, int quantity, int sizeVn,
                        String productName, String productImg) {
        this.id = id;
        this.totalPrice = totalPrice;
        this.productPrice = productPrice;
        this.quantity = quantity;
        this.sizeVn = sizeVn;
        this.productName = productName;
        this.productImg = productImg;
    }

    /**
     * Tinh thanh tien thuc khach phai tra.
     * Heuristic: gia tri lon hon trong (totalPrice, productPrice - totalPrice) = final.
     * (Vi discount thuong < 50% gia goc.)
     */
    public long getFinalPrice() {
        long v1 = this.totalPrice;
        long v2 = this.productPrice - this.totalPrice;
        if (v1 < 0) v1 = 0;
        if (v2 < 0) v2 = 0;
        return Math.max(v1, v2);
    }

    /** So tien duoc giam. */
    public long getDiscount() {
        long v1 = this.totalPrice;
        long v2 = this.productPrice - this.totalPrice;
        if (v1 < 0) v1 = 0;
        if (v2 < 0) v2 = 0;
        return Math.min(v1, v2);
    }
}
