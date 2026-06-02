package com.davidcreate.jobhub.application.adapter.out.persistence.entity;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApplicationStatusConverter implements AttributeConverter<ApplicationStatus, String> {

    @Override
    public String convertToDatabaseColumn(ApplicationStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ApplicationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ApplicationStatus.fromDbValue(dbData);
    }
}
