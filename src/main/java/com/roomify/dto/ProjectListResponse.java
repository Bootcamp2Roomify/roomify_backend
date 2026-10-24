package com.roomify.dto;

import java.util.List;

public record ProjectListResponse(List<ProjectSummaryResponse> projects, int page, int pageSize, boolean hasNext) {
}
