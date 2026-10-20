package com.ht.robotvacuum.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CrudPageRequest {

    private CrudPageRequest() {
    }

    static Pageable of(int page, int size, String[] sort, Set<String> allowed) {
        Assert.isTrue(page >= 0 && size >= 1 && size <= 100, "Invalid page or size");
        List<Sort.Order> orders = new ArrayList<>();
        // Read raw repeated query parameters to avoid Spring splitting a single sort on commas.
        for (String value : sort) {
            String[] parts = value.split(",", -1);
            Assert.isTrue(parts.length == 2 && allowed.contains(parts[0]), "Invalid sort field");
            String direction = parts[1].toLowerCase(Locale.ROOT);
            Assert.isTrue(direction.equals("asc") || direction.equals("desc"), "Invalid sort direction");
            orders.add(new Sort.Order(Sort.Direction.fromString(direction), parts[0]));
        }
        if (orders.stream().noneMatch(order -> order.getProperty().equals("id"))) {
            orders.add(Sort.Order.asc("id"));
        }
        return PageRequest.of(page, size, Sort.by(orders));
    }
}
