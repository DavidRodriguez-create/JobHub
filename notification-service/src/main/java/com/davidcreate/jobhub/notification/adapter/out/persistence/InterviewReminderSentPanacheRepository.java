package com.davidcreate.jobhub.notification.adapter.out.persistence;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.InterviewReminderSentEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.InterviewReminderSentMapper;
import com.davidcreate.jobhub.notification.domain.model.InterviewReminderSent;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import com.davidcreate.jobhub.notification.domain.port.out.InterviewReminderSentRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class InterviewReminderSentPanacheRepository
        implements InterviewReminderSentRepository, PanacheRepositoryBase<InterviewReminderSentEntity, UUID> {

    private final InterviewReminderSentMapper mapper;

    public InterviewReminderSentPanacheRepository(InterviewReminderSentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean exists(UUID userId, UUID applicationId, ReminderOffset reminderOffset) {
        return count("userId = ?1 and applicationId = ?2 and reminderOffset = ?3",
                userId, applicationId, reminderOffset.name()) > 0;
    }

    @Override
    @Transactional
    public InterviewReminderSent save(InterviewReminderSent interviewReminderSent) {
        InterviewReminderSentEntity entity = mapper.toEntity(interviewReminderSent);
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }
        persist(entity);
        return mapper.toDomain(entity);
    }
}
