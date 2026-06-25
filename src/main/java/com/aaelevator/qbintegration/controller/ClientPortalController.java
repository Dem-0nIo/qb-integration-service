package com.aaelevator.qbintegration.controller;

import com.aaelevator.qbintegration.dto.ConsolidatedViewDTO;
import com.aaelevator.qbintegration.service.ConsolidatedViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/client")
public class ClientPortalController {

    @Autowired
    private ConsolidatedViewService consolidatedViewService;

    @GetMapping("/consolidated-view")
    public List<ConsolidatedViewDTO> getConsolidatedView(
            @RequestParam String qbEmpresa,
            @RequestParam(defaultValue = "12") int months) {
        return consolidatedViewService.getMaintenanceView(qbEmpresa, months);
    }
}
