package com.davidcreate.jobhub.application.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.UserJobPostEntity;
import com.davidcreate.jobhub.application.adapter.out.persistence.mapper.UserJobPostMapper;
import com.davidcreate.jobhub.application.domain.entity.UserJobPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserJobPostMapper Unit Tests")
class UserJobPostMapperTest {

    private final UserJobPostMapper mapper = new UserJobPostMapper();

    @Test
    @DisplayName("round-trips fields between entity and domain")
    void roundTrip() {
        var e = new UserJobPostEntity();
        e.id = UUID.randomUUID();
        e.userId = UUID.randomUUID();
        e.title = "Dev";
        e.company = "Acme";
        e.url = "https://acme";
        e.location = "Madrid, Spain";
        e.createdAt = OffsetDateTime.now();
        e.updatedAt = OffsetDateTime.now();

        UserJobPost d = mapper.toDomain(e);
        UserJobPostEntity back = mapper.toEntity(d);

        assertThat(d.getId()).isEqualTo(e.id);
        assertThat(d.getTitle()).isEqualTo("Dev");
        assertThat(back.title).isEqualTo("Dev");
        assertThat(back.company).isEqualTo("Acme");
        assertThat(back.location).isEqualTo("Madrid, Spain");
    }

    @Test
    @DisplayName("updateEntity overwrites all mutable fields")
    void updateEntityOverwrites() {
        var e = new UserJobPostEntity();
        e.title = "Old";
        e.company = "OldCo";
        e.url = "old";
        e.location = "oldLocation";

        var d = UserJobPost.builder()
                .title("New").company("NewCo").url(null).location("c").build();
        mapper.updateEntity(e, d);

        assertThat(e.title).isEqualTo("New");
        assertThat(e.company).isEqualTo("NewCo");
        assertThat(e.url).isNull();
        assertThat(e.location).isEqualTo("c");
    }
}
