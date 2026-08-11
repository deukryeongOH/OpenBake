package com.openbake.product.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.product.application.ProductService;
import com.openbake.product.application.dto.GeneralProductInfoCommand;
import com.openbake.product.application.dto.GeneralProductInfoResult;
import com.openbake.product.presentation.dto.GeneralProductInfoRequest;
import com.openbake.product.presentation.dto.GeneralProductInfoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping("/register")
    public ApiResponse<GeneralProductInfoResponse> registerGeneralProduct(@Valid @RequestBody GeneralProductInfoRequest request){
        GeneralProductInfoCommand command = GeneralProductInfoCommand.create(request.name(), request.description(),
                request.imageUrl(), request.totalQuantity(),
                request.price(), request.pickUpAvailableDates(), request.category());

        GeneralProductInfoResult result = productService.registerGeneralProduct(command);
        return ApiResponse.ok(GeneralProductInfoResponse.of(result));
    }

    @PutMapping("/{productId}")
    public ApiResponse<GeneralProductInfoResponse> updateGeneralProduct(@Valid @RequestBody GeneralProductInfoRequest request, @PathVariable Long productId){
        GeneralProductInfoCommand command = GeneralProductInfoCommand.create(request.name(), request.description(),
                request.imageUrl(), request.totalQuantity(),
                request.price(), request.pickUpAvailableDates(), request.category());

        GeneralProductInfoResult response = productService.updateGeneralProduct(command, productId);
        return ApiResponse.ok(GeneralProductInfoResponse.of(response));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<String> deleteGeneralProduct(@PathVariable Long productId){
        productService.deleteGeneralProduct(productId);
        return ApiResponse.ok("삭제 완료");
    }

    // 판매자 본인이 등록한 일반 상품 리스트 보여주기
    @GetMapping("/seller-product-list")
    public ApiResponse<List<GeneralProductInfoResponse>> getSellerGeneralProductList(){

        List<GeneralProductInfoResult> productList = productService.getSellerGeneralProductList();
        return ApiResponse.ok(productList.stream().map(GeneralProductInfoResponse::of).toList());
    }


    // 홈 화면에 상품 리스트 보여주기
    @GetMapping("/product-list")
    public ApiResponse<List<GeneralProductInfoResponse>> getGeneralProductList(){
        List<GeneralProductInfoResult> productList = productService.getGeneralProductList();

        return ApiResponse.ok(productList.stream().map(GeneralProductInfoResponse::of).toList());
    }

}