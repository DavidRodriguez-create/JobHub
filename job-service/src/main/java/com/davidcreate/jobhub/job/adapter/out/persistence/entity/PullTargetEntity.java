package com.davidcreate.jobhub.job.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "pull_target", schema = "crawler")
public class PullTargetEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "source_type", nullable = false)
    public String sourceType;

    @Column(name = "company_name", nullable = false)
    public String companyName;

    @Column(name = "company_logo_url")
    public String companyLogoUrl;
}
