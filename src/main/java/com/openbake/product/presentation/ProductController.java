package com.openbake.product.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.product.application.ProductService;
import com.openbake.product.application.dto.ProductInfoCommand;
import com.openbake.product.application.dto.ProductInfoResult;
import com.openbake.product.presentation.dto.ProductInfoRequest;
import com.openbake.product.presentation.dto.ProductInfoResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping("/register")
    public ApiResponse<ProductInfoResponse> registerGeneralProduct(@Valid @RequestBody ProductInfoRequest request){
        ProductInfoCommand command = ProductInfoCommand.create(request.name(), request.description(),
                request.imageUrl(), request.totalQuantity(),
                request.price(), request.pickUpAvailableDates(), request.category());

        ProductInfoResult result = productService.registerGeneralProduct(command);
        return ApiResponse.ok(ProductInfoResponse.of(result));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductInfoResponse> updateGeneralProduct(@Valid @RequestBody ProductInfoRequest request, @PathVariable Long productId){
        ProductInfoCommand command = ProductInfoCommand.create(request.name(), request.description(),
                request.imageUrl(), request.totalQuantity(),
                request.price(), request.pickUpAvailableDates(), request.category());

        ProductInfoResult response = productService.updateGeneralProduct(command, productId);
        return ApiResponse.ok(ProductInfoResponse.of(response));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<String> deleteGeneralProduct(@PathVariable Long productId){
        productService.deleteGeneralProduct(productId);
        return ApiResponse.ok("삭제 완료");
    }

    // 판매자 본인이 등록한 일반 상품 리스트 보여주기
    @GetMapping("/seller-product-list")
    public ApiResponse<PagedModel<ProductInfoResponse>> getSellerGeneralProductList(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ){

        Page<ProductInfoResult> productList = productService.getSellerProductList(pageable);
        return ApiResponse.ok(new PagedModel<>(productList.map(ProductInfoResponse::of)));
    }


    // 홈 화면에 상품 리스트 보여주기 + 검색
    @GetMapping("/product-list")
    public ApiResponse<PagedModel<ProductInfoResponse>> getGeneralProductList(
            @PageableDefault(size = 20, sort = "category", direction = Sort.Direction.ASC) Pageable pageable
            ){
        Page<ProductInfoResult> productPage = productService.getProductList(pageable);


        return ApiResponse.ok(new PagedModel<>(productPage.map(ProductInfoResponse::of)));
    }

}