package com.phs.application.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class AddCartItemRequest {

    @NotBlank(message = "Sản phẩm trống")
    @JsonProperty("product_id")
    private String productId;

    @Min(value = 35, message = "Size không hợp lệ")
    @Max(value = 42, message = "Size không hợp lệ")
    private int size;

    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    private int quantity;
}
