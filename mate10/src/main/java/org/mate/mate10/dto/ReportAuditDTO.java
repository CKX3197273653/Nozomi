package org.mate.mate10.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReportAuditDTO {
    private String reportNo;
    private Integer versionCount;
    private ConclusionView current;
    private List<ConclusionView> conclusionTimeline;
    private List<DiscussionView> discussions;

    @Data
    @Builder
    public static class ConclusionView {
        private Integer version;
        private String conclusion;
        private String advice;
        private String status;
        private Boolean superseded;
        private Long analyzedBy;
        private LocalDateTime analyzedAt;
    }

    @Data
    @Builder
    public static class DiscussionView {
        private Long chatId;
        private String role;
        private String content;
        private LocalDateTime createTime;
    }
}
