package com.delta.store.product.service;

 

import com.delta.store.product.dto.ShopProductDetailDTO;

public interface ShopProductDetailService {
    ShopProductDetailDTO buildDetail(Long productId);
}