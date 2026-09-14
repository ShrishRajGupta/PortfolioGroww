package com.example.demo.controller;

import com.example.demo.dto.PositionDrift;
import com.example.demo.dto.RebuildResult;
import com.example.demo.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Operational endpoints for the materialized positions. Dev profile only: the application has no
 * authentication yet, and reconcile mutates data — in production this belongs behind an
 * authenticated admin surface.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/admin/positions")
@RequiredArgsConstructor
public class AdminController {

    private final PositionService positionService;

    /** Rebuilds positions from the ledger — one user, or everyone when {@code userId} is omitted. */
    @PostMapping("/reconcile")
    public RebuildResult reconcile(@RequestParam(required = false) Long userId) {
        return userId == null ? positionService.rebuildAll() : positionService.rebuild(userId);
    }

    /** Ledger-vs-positions comparison for one user; an empty list means no drift. */
    @GetMapping("/drift")
    public List<PositionDrift> drift(@RequestParam Long userId) {
        return positionService.drift(userId);
    }
}
