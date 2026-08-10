package com.openbake.order.infrastructure.client;

import com.openbake.common.response.ApiResponse;
import com.openbake.order.application.port.MemberPort;
import com.openbake.order.application.port.dto.MemberInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "member-service", url = "${openbake.member-service.url}")
public interface MemberFeignClient extends MemberPort {

    @GetMapping("/internal/v1/members/{memberId}")
    ApiResponse<MemberInfo> getMember(@PathVariable Long memberId);
}
