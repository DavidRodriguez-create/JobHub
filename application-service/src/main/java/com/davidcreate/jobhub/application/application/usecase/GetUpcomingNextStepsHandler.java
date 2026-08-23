package com.davidcreate.jobhub.application.application.usecase;

import com.davidcreate.jobhub.application.application.port.in.GetUpcomingNextStepsUseCase;
import com.davidcreate.jobhub.application.application.port.out.UpcomingNextStepRepository;
import com.davidcreate.jobhub.application.domain.entity.UpcomingNextStep;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
public class GetUpcomingNextStepsHandler implements GetUpcomingNextStepsUseCase {

    private final UpcomingNextStepRepository repository;

    @Override
    public List<UpcomingNextStep> handle(int withinHours) {
        return repository.findUpcoming(withinHours);
    }
}
