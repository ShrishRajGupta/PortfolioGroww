package com.example.demo.web;

import com.example.demo.controller.AdminController;
import com.example.demo.dto.PositionDrift;
import com.example.demo.dto.RebuildResult;
import com.example.demo.service.PositionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@ActiveProfiles("dev")
class AdminControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PositionService positionService;

    @Test
    void reconcile_withoutUser_rebuildsEveryone() throws Exception {
        when(positionService.rebuildAll()).thenReturn(new RebuildResult(10, 47));

        mvc.perform(post("/api/admin/positions/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersRebuilt").value(10))
                .andExpect(jsonPath("$.positionsWritten").value(47));
    }

    @Test
    void reconcile_withUser_rebuildsThatUser() throws Exception {
        when(positionService.rebuild(3L)).thenReturn(new RebuildResult(1, 4));

        mvc.perform(post("/api/admin/positions/reconcile").param("userId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersRebuilt").value(1))
                .andExpect(jsonPath("$.positionsWritten").value(4));
        verify(positionService).rebuild(3L);
    }

    @Test
    void drift_returnsMismatches() throws Exception {
        when(positionService.drift(1L)).thenReturn(List.of(
                new PositionDrift(2L, 6, 5L, new BigDecimal("103.24964444"), new BigDecimal("103.24964444"))));

        mvc.perform(get("/api/admin/positions/drift").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stockId").value(2))
                .andExpect(jsonPath("$[0].ledgerNetQuantity").value(6))
                .andExpect(jsonPath("$[0].storedNetQuantity").value(5));
    }

    @Test
    void drift_requiresUserId() throws Exception {
        mvc.perform(get("/api/admin/positions/drift"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }
}
