package com.phs.application.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Min;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class UpdateCartItemRequest {

    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    private int quantity;
}
