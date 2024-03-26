package com.phs.application.model.mapper;

import com.phs.application.entity.Brand;
import com.phs.application.model.request.CreateBrandRequest;
import com.phs.application.model.dto.BrandDTO;

import java.sql.Timestamp;

public class BrandMapper {
    public static BrandDTO toBrandDTO(Brand brand){
        BrandDTO brandDTO = new BrandDTO();
        brandDTO.setId(brand.getId());
        brandDTO.setName(brand.getName());
        brandDTO.setDescription(brand.getDescription());
        brandDTO.setThumbnail(brand.getThumbnail());
        brandDTO.setStatus(brand.isStatus());

        return brandDTO;
    }

    public static Brand toBrand(CreateBrandRequest createBrandRequest){
        Brand brand= new Brand();
        brand.setName(createBrandRequest.getName());
        brand.setDescription(createBrandRequest.getDescription());
        brand.setThumbnail(createBrandRequest.getThumbnail());
        brand.setStatus(createBrandRequest.isStatus());
        brand.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        return brand;
    }
}
