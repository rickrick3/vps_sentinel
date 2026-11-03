package com.expertiz.sentinel.core.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

    private static final SortSpec SPEC = new SortSpec(Map.of("name", "name", "createdAt", "createdAt"), "name,asc");

    @Test
    void defaults() {
        Pageable pageable = PageRequests.of(null, null, null, SPEC);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    void rejectsSizeOver100() {
        assertViolationOn(() -> PageRequests.of(0, 101, null, SPEC), "size");
    }

    @Test
    void rejectsSizeZero() {
        assertViolationOn(() -> PageRequests.of(0, 0, null, SPEC), "size");
    }

    @Test
    void rejectsNegativePage() {
        assertViolationOn(() -> PageRequests.of(-1, 20, null, SPEC), "page");
    }

    @Test
    void rejectsUnknownSort() {
        assertViolationOn(() -> PageRequests.of(0, 20, "description,asc", SPEC), "sort");
    }

    @Test
    void rejectsUnknownDirection() {
        assertViolationOn(() -> PageRequests.of(0, 20, "name,sideways", SPEC), "sort");
    }

    @Test
    void mapsApiFieldToProperty() {
        Pageable pageable = PageRequests.of(2, 50, "createdAt,desc", SPEC);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void pageResponseFromPage() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        assertThat(PageResponse.from(page)).isEqualTo(new PageResponse<>(List.of("a", "b"), 1, 2, 5, 3));
    }

    private static void assertViolationOn(Runnable call, String field) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(ProblemType.VALIDATION_ERROR);
                    assertThat(ex.errors()).extracting(FieldViolation::field).containsExactly(field);
                });
    }
}
