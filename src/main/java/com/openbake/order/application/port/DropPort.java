package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.DropInfo;

public interface DropPort {
    DropInfo getDrop(Long dropId);

    Long getProductId(Long dropId);
}
