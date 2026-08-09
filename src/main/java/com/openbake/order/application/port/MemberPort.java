package com.openbake.order.application.port;

import com.openbake.common.response.ApiResponse;
import com.openbake.order.application.port.dto.MemberInfo;
import org.springframework.web.bind.annotation.PathVariable;

public interface MemberPort {
    ApiResponse<MemberInfo> getMember(@PathVariable Long memberId);
}
