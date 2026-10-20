package com.ht.robotvacuum.dto.response;

import org.springframework.data.domain.Page;
import java.util.List;

public record ResPageDTO<T>(List<T> content, int page, int size, long totalElements,
                            int totalPages, boolean first, boolean last) {

    public static <T> ResPageDTO<T> from(Page<T> page) {
        return new ResPageDTO<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
