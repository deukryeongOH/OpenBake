package com.openbake.product.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.product.application.ProductService;
import com.openbake.product.application.S3ImageService;
import com.openbake.product.application.dto.PresignedUploadResult;
import com.openbake.product.application.dto.ProductInfoCommand;
import com.openbake.product.application.dto.ProductInfoResult;
import com.openbake.product.presentation.dto.ImageUploadUrlRequest;
import com.openbake.product.presentation.dto.ProductInfoRequest;
import com.openbake.product.presentation.dto.ProductInfoResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Product", description = "일반 상품 등록/수정/삭제/조회, 상품 이미지 업로드 URL 발급")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final S3ImageService s3ImageService;

    @Operation(
            summary = "일반 상품 등록",
            description = "판매자가 일반 상품을 등록합니다. imageUrl에는 미리 /image-upload-url로 발급받아 S3에 업로드한 임시 키를 넣어야 하며, 등록 시 서버가 해당 이미지를 최종 경로로 옮긴 뒤 상품에 반영합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PR006 상품 이미지를 업로드 해주세요.")
    })
    @PostMapping("/register")
    public ApiResponse<ProductInfoResponse> registerGeneralProduct(@Valid @RequestBody ProductInfoRequest request){
        ProductInfoCommand command = ProductInfoCommand.create(request.name(), request.description(),
                request.imageUrl(), request.totalQuantity(),
                request.price(), request.pickUpAvailableDates(), request.category());

        ProductInfoResult result = productService.registerGeneralProduct(command);// 상품 등록하고

        String imageUrl = s3ImageService.promote(request.imageUrl(), result.productId()); // S3에 업로드

        productService.updateImageUrl(result.productId(), imageUrl); // S3에 올린 이미지 경로로 업데이트

        return ApiResponse.ok(ProductInfoResponse.of(result));
    }

    @Operation(
            summary = "일반 상품 수정",
            description = "판매자 본인이 등록한 일반 상품을 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. / PR002 판매자 본인의 상품이 아닙니다. / PR005 상품 타입 불일치 / DR015 복구할 재고와 남아있는 재고의 합이 총 발매 수량보다 클 수 없습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PR001 존재하지 않는 일반 상품입니다.")
    })
    @PutMapping("/{productId}")
    public ApiResponse<ProductInfoResponse> updateGeneralProduct(@Valid @RequestBody ProductInfoRequest request,
            @Parameter(description = "수정할 상품 ID", example = "1") @PathVariable Long productId){
        ProductInfoCommand command = ProductInfoCommand.create(request.name(), request.description(),
                request.imageUrl(), request.totalQuantity(),
                request.price(), request.pickUpAvailableDates(), request.category());

        ProductInfoResult response = productService.updateGeneralProduct(command, productId);
        return ApiResponse.ok(ProductInfoResponse.of(response));
    }

    @Operation(
            summary = "일반 상품 삭제",
            description = "판매자 본인이 등록한 일반 상품을 삭제합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "PR002 판매자 본인의 상품이 아닙니다. / PR005 상품 타입 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PR001 존재하지 않는 일반 상품입니다.")
    })
    @DeleteMapping("/{productId}")
    public ApiResponse<String> deleteGeneralProduct(
            @Parameter(description = "삭제할 상품 ID", example = "1") @PathVariable Long productId){
        productService.deleteGeneralProduct(productId);
        return ApiResponse.ok("삭제 완료");
    }

    @Operation(
            summary = "내 일반 상품 목록 조회",
            description = "판매자 본인이 등록한 일반 상품 목록을 페이지 단위로 조회합니다."
    )
    // 판매자 본인이 등록한 일반 상품 리스트 보여주기
    @GetMapping("/seller-product-list")
    public ApiResponse<PagedModel<ProductInfoResponse>> getSellerGeneralProductList(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ){

        Page<ProductInfoResult> productList = productService.getSellerProductList(pageable);
        return ApiResponse.ok(new PagedModel<>(productList.map(ProductInfoResponse::of)));
    }


    @Operation(
            summary = "일반 상품 목록 조회",
            description = "홈 화면에 노출할 일반 상품 목록을 카테고리 순으로 페이지 단위 조회합니다."
    )
    // 홈 화면에 상품 리스트 보여주기 + 검색
    @GetMapping("/product-list")
    public ApiResponse<PagedModel<ProductInfoResponse>> getGeneralProductList(
            @PageableDefault(size = 20, sort = "category", direction = Sort.Direction.ASC) Pageable pageable
            ){
        Page<ProductInfoResult> productPage = productService.getProductList(pageable);


        return ApiResponse.ok(new PagedModel<>(productPage.map(ProductInfoResponse::of)));
    }

    @Operation(
            summary = "상품 이미지 업로드 URL 발급",
            description = "상품 등록/수정 전, 이미지를 S3 임시 경로(uploads/tmp/)에 직접 업로드할 수 있는 presigned PUT URL을 발급합니다. 발급된 URL은 3분간 유효하며, 클라이언트가 이 URL로 이미지를 PUT한 뒤 응답의 key를 상품 등록/수정 요청의 imageUrl로 전달해야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (Content-Type 누락)")
    })
    @PostMapping("/image-upload-url") // 서버에 접근해서 S3 버킷의 presigned Url을 얻기 위한 Controller
    public ApiResponse<PresignedUploadResult> uploadImageS3(@Valid @RequestBody ImageUploadUrlRequest request){
        PresignedUploadResult result = s3ImageService.issueUploadUrl(request.contentType());
        return ApiResponse.ok(result);
    }

}