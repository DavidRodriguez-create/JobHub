package com.davidcreate.jobhub.application.adapter.out.persistence.entity;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "application_timeline", schema = "applications")
public class ApplicationTimelineEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "application_id", nullable = false)
    public UUID applicationId;

    @Column(name = "status", nullable = false, columnDefinition = "applications.status")
    @Convert(converter = ApplicationStatusConverter.class)
    @ColumnTransformer(read = "status::text", write = "?::applications.status")
    public ApplicationStatus status;

    @Column(name = "occurred_at", nullable = false)
    public OffsetDateTime occurredAt;
}
