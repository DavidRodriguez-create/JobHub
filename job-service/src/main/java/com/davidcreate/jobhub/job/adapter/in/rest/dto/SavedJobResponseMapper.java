package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.SavedJobPage;
import com.davidcreate.jobhub.job.contract.model.SavedJobResponse;
import com.davidcreate.jobhub.job.domain.model.SavedJobView;
import com.davidcreate.jobhub.job.domain.port.in.SavedJobUseCase.SavedJobsPage;

public final class SavedJobResponseMapper {

    private SavedJobResponseMapper() {}

    public static SavedJobPage toPage(SavedJobsPage result, int page, int size) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) result.total() / size);
        return new SavedJobPage()
                .content(result.items().stream().map(SavedJobResponseMapper::toResponse).toList())
                .page(page)
                .size(size)
                .totalElements(result.total())
                .totalPages(totalPages);
    }

    private static SavedJobResponse toResponse(SavedJobView view) {
        return new SavedJobResponse()
                .savedAt(view.savedAt())
                .job(JobPostResponseMapper.toResponse(view.job()));
    }
}
