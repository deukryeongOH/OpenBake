package com.openbake.drop.presentation;

import com.openbake.drop.application.DropService;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.domain.DropStatus;
import com.openbake.seller.application.CurrentSellerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DropController.class)
@AutoConfigureMockMvc(addFilters = false)
class DropControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DropService dropService;

    @MockitoBean
    private CurrentSellerProvider currentSellerProvider;

    @Test
    @DisplayName("예정된 드롭 목록을 조회한다")
    void getUpcomingDrops() throws Exception {
        DropProductInfoResult result = new DropProductInfoResult(
                "버터떡",
                "버터를 많이 써서 향이 좋아요.",
                "https://cdn.openbake.com/drops/12.jpg",
                new HashSet<>(),
                LocalDateTime.parse("2028-08-01T14:00:00"),
                LocalDateTime.parse("2028-08-01T18:00:00"),
                5,
                3000,
                200,
                200,
                DropStatus.UPCOMING,
                12L
        );

        when(dropService.getUpcomingDrops(7))
                .thenReturn(List.of(result));

        mockMvc.perform(get("/api/v1/drops/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].dropId").value(12))
                .andExpect(jsonPath("$.data[0].name").value("버터떡"))
                .andExpect(jsonPath("$.data[0].dropStatus").value("UPCOMING"));
    }

    @Test
    @DisplayName("days 파라미터로 조회 기간을 지정한다")
    void getUpcomingDrops_withDays() throws Exception {
        when(dropService.getUpcomingDrops(3))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/drops/upcoming").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
