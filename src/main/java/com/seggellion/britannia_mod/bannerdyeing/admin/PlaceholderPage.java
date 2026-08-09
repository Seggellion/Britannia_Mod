package com.seggellion.britannia_mod.bannerdyeing.admin;

import java.util.List;

public record PlaceholderPage(
        List<PlaceholderEntry> entries, int requestedPage, int page, int pageCount, int totalCount, int pageSize) {
    public PlaceholderPage {
        entries = List.copyOf(entries);
    }
}
