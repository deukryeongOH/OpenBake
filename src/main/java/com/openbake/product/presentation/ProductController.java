package com.openbake.product.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.product.application.ProductService;
import com.openbake.product.application.dto.GeneralProductInfoCommand;
import com.openbake.product.application.dto.GeneralProductInfoResult;
import com.openbake.product.domain.Category;
import com.openbake.product.presentation.dto.GeneralProductInfoRequest;
import com.openbake.product.presentation.dto.GeneralProductInfoResponse;
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
    public ApiResponse<GeneralProductInfoResponse> registerGeneralProduct(@Valid @RequestBody GeneralProductInfoRequest request){
        GeneralProductInfoCommand command = GeneralProductInfoCommand.create(request.name(), request.description(),
                request.imageUrl(), request.totalQuantity(),
                request.price(), request.pickUpAvailableDates(), request.category(), request.type());

        GeneralProductInfoResult result = productService.registerGeneralProduct(command);
        return ApiResponse.ok(GeneralProductInfoResponse.of(result));
    }

    @PutMapping("/{productId}")
    public ApiResponse<GeneralProductInfoResponse> updateGeneralProduct(@Valid @RequestBody GeneralProductInfoRequest request, @PathVariable Long productId){
        GeneralProductInfoCommand command = GeneralProductInfoCommand.create(request.name(), request.description(),
                request.imageUrl(), request.totalQuantity(),
                request.price(), request.pickUpAvailableDates(), request.category(), request.type());

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
    public ApiResponse<PagedModel<GeneralProductInfoResponse>> getSellerGeneralProductList(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ){

        Page<GeneralProductInfoResult> productList = productService.getSellerGeneralProductList(pageable);
        return ApiResponse.ok(new PagedModel<>(productList.map(GeneralProductInfoResponse::of)));
    }


    // 홈 화면에 상품 리스트 보여주기 + 검색
    @GetMapping("/product-list")
    public ApiResponse<PagedModel<GeneralProductInfoResponse>> getGeneralProductList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Category category,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
            ){
        Page<GeneralProductInfoResult> productPage = productService.getGeneralProductList(keyword, category, pageable);

        return ApiResponse.ok(new PagedModel<>(productPage.map(GeneralProductInfoResponse::of)));
    }

}