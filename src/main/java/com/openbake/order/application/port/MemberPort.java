package com.openbake.order.application.port;

import com.openbake.common.response.ApiResponse;
import com.openbake.order.application.port.dto.MemberInfo;

public interface MemberPort {
    ApiResponse<MemberInfo> getMember(Long memberId);
}
