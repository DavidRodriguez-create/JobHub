package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.exception.JobNotFoundException;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.job.domain.port.out.SavedJobRepository;
import com.davidcreate.jobhub.job.domain.service.SavedJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavedJobService Unit Tests")
class SavedJobServiceTest {

    @Mock SavedJobRepository savedJobRepository;
    @Mock JobPostRepository jobPostRepository;
    @InjectMocks SavedJobService service;

    private final UUID user = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @Test
    @DisplayName("save bookmarks a new job")
    void saveNew() {
        when(jobPostRepository.findJobById(jobId)).thenReturn(Optional.of(JobPost.builder().id(jobId).build()));
        when(savedJobRepository.exists(user, jobId)).thenReturn(false);

        service.save(user, jobId);

        verify(savedJobRepository).add(user, jobId);
    }

    @Test
    @DisplayName("save is idempotent — does not re-add an existing bookmark")
    void saveIdempotent() {
        when(jobPostRepository.findJobById(jobId)).thenReturn(Optional.of(JobPost.builder().id(jobId).build()));
        when(savedJobRepository.exists(user, jobId)).thenReturn(true);

        service.save(user, jobId);

        verify(savedJobRepository, never()).add(user, jobId);
    }

    @Test
    @DisplayName("save throws JobNotFound when the job does not exist")
    void saveMissingJob() {
        when(jobPostRepository.findJobById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(user, jobId)).isInstanceOf(JobNotFoundException.class);
        verify(savedJobRepository, never()).add(any(), any());
    }

    @Test
    @DisplayName("unsave delegates to the repository")
    void unsave() {
        service.unsave(user, jobId);
        verify(savedJobRepository).remove(user, jobId);
    }

    @Test
    @DisplayName("list clamps page size to 100")
    void listClampsSize() {
        when(savedJobRepository.listByUser(user, 0, 100)).thenReturn(List.of());
        when(savedJobRepository.countByUser(user)).thenReturn(0L);

        service.list(user, 0, 9999);

        verify(savedJobRepository).listByUser(user, 0, 100);
    }
}
