package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.exception.SavedFilterLimitException;
import com.davidcreate.jobhub.job.domain.exception.SavedFilterNotFoundException;
import com.davidcreate.jobhub.job.domain.model.SavedFilter;
import com.davidcreate.jobhub.job.domain.service.SavedFilterService;
import com.davidcreate.jobhub.job.domain.port.out.SavedFilterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavedFilterService Unit Tests")
class SavedFilterServiceTest {

    @Mock SavedFilterRepository repository;
    @InjectMocks SavedFilterService service;

    private final UUID user = UUID.randomUUID();

    @Test
    @DisplayName("create saves a preset when under the limit")
    void createUnderLimit() {
        when(repository.countByUser(user)).thenReturn(3L);
        when(repository.save(any(SavedFilter.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(user, "  My Preset  ", "{\"keyword\":\"java\"}");

        ArgumentCaptor<SavedFilter> cap = ArgumentCaptor.forClass(SavedFilter.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("My Preset");
        assertThat(cap.getValue().getUserId()).isEqualTo(user);
        assertThat(cap.getValue().getFiltersJson()).isEqualTo("{\"keyword\":\"java\"}");
    }

    @Test
    @DisplayName("create rejects a 6th preset with SavedFilterLimit")
    void createAtLimit() {
        when(repository.countByUser(user)).thenReturn(5L);

        assertThatThrownBy(() -> service.create(user, "X", "{}"))
                .isInstanceOf(SavedFilterLimitException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update renames and leaves filters untouched when only name is supplied")
    void updateRename() {
        UUID id = UUID.randomUUID();
        SavedFilter existing = SavedFilter.builder()
                .id(id).userId(user).name("Old").filtersJson("{\"keyword\":\"go\"}").build();
        when(repository.findByIdAndUser(id, user)).thenReturn(Optional.of(existing));
        when(repository.save(any(SavedFilter.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedFilter updated = service.update(user, id, "New", null);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getFiltersJson()).isEqualTo("{\"keyword\":\"go\"}");
    }

    @Test
    @DisplayName("update throws when the preset is missing or not owned")
    void updateMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUser(id, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(user, id, "New", null))
                .isInstanceOf(SavedFilterNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("delete removes an owned preset")
    void deleteOwned() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUser(id, user))
                .thenReturn(Optional.of(SavedFilter.builder().id(id).userId(user).build()));

        service.delete(user, id);

        verify(repository).removeById(id);
    }

    @Test
    @DisplayName("delete throws when the preset is missing")
    void deleteMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUser(id, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(user, id))
                .isInstanceOf(SavedFilterNotFoundException.class);
        verify(repository, never()).removeById(any());
    }
}
