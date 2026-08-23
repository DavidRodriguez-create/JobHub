package com.davidcreate.jobhub.application.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.application.adapter.in.rest.dto.ApplicationStatusMapper;
import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepItem;
import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepsResponse;
import com.davidcreate.jobhub.application.domain.entity.UpcomingNextStep;

import java.util.List;

/**
 * Maps {@link UpcomingNextStep} domain rows to the generated
 * {@code UpcomingNextStepsResponse}/{@code UpcomingNextStepItem} contract models for
 * {@code GET /internal/applications/upcoming-next-steps} (ADR 0009).
 */
public final class UpcomingNextStepMapper {

    private UpcomingNextStepMapper() {
    }

    public static UpcomingNextStepItem toResponseItem(UpcomingNextStep row) {
        return new UpcomingNextStepItem()
                .userId(row.getUserId())
                .applicationId(row.getApplicationId())
                .nextStepLabel(row.getNextStepLabel())
                .nextStepDate(row.getNextStepDate())
                .nextStepReminderAt(row.getNextStepReminderAt())
                .companyName(row.getCompanyName())
                .status(ApplicationStatusMapper.toContract(row.getStatus()));
    }

    public static UpcomingNextStepsResponse toResponse(List<UpcomingNextStep> rows) {
        UpcomingNextStepsResponse response = new UpcomingNextStepsResponse();
        rows.forEach(row -> response.addItemsItem(toResponseItem(row)));
        return response;
    }
}
