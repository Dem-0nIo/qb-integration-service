package com.aaelevator.qbintegration.dto;

import java.time.LocalDate;

public class TicketSummaryDTO {

    private Integer ticketId;
    private LocalDate dateCallIn;
    private String descriptionRequest;
    private String descriptionWork;
    private String workComplete;
    private String ticketType;
    private String buildingName;

    public TicketSummaryDTO(Integer ticketId, LocalDate dateCallIn, String descriptionRequest,
                            String descriptionWork, String workComplete, String ticketType, String buildingName) {
        this.ticketId = ticketId;
        this.dateCallIn = dateCallIn;
        this.descriptionRequest = descriptionRequest;
        this.descriptionWork = descriptionWork;
        this.workComplete = workComplete;
        this.ticketType = ticketType;
        this.buildingName = buildingName;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public LocalDate getDateCallIn() {
        return dateCallIn;
    }

    public String getDescriptionRequest() {
        return descriptionRequest;
    }

    public String getDescriptionWork() {
        return descriptionWork;
    }

    public String getWorkComplete() {
        return workComplete;
    }

    public String getTicketType() {
        return ticketType;
    }

    public String getBuildingName() {
        return buildingName;
    }
}