package org.mate.mate10.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.document.ChatMessageDocument;
import org.mate.mate10.document.ReportMemoryDocument;
import org.mate.mate10.dto.ReportAuditDTO;
import org.mate.mate10.repository.ChatMessageRepository;
import org.mate.mate10.repository.ReportMemoryRepository;
import org.mate.mate10.service.ReportAuditService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportAuditServiceImpl implements ReportAuditService {

    private final ReportMemoryRepository memoryRepository;
    private final ChatMessageRepository messageRepository;

    @Override
    public ReportAuditDTO audit(String reportNo) {
        //结论版本链（含被覆盖的历史版本，时间正序）
        List<ReportMemoryDocument> versions =
                memoryRepository.findByReportNoOrderByAnalyzedAtAsc(reportNo);

        List<ReportAuditDTO.ConclusionView> timeline = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            timeline.add(toConclusionView(versions.get(i), i + 1));   // 序号即版本号
        }

        //当前有效结论 = 版本链里最后一个 superseded=false 的
        ReportAuditDTO.ConclusionView current = null;
        for (ReportAuditDTO.ConclusionView v : timeline) {
            if (!Boolean.TRUE.equals(v.getSuperseded())) {
                current = v;
            }
        }

        //讨论记录
        List<ReportAuditDTO.DiscussionView> discussions =
                messageRepository.findByReportNoOrderByCreateTimeAsc(reportNo)
                        .stream()
                        .map(this::toDiscussionView)
                        .toList();

        log.info("[Audit] reportNo={} 结论版本 {} 次，讨论记录 {} 条",
                reportNo, timeline.size(), discussions.size());

        return ReportAuditDTO.builder()
                .reportNo(reportNo)
                .versionCount(timeline.size())
                .current(current)
                .conclusionTimeline(timeline)
                .discussions(discussions)
                .build();
    }

    private ReportAuditDTO.ConclusionView toConclusionView(ReportMemoryDocument d, int version) {
        return ReportAuditDTO.ConclusionView.builder()
                .version(version)
                .conclusion(d.getConclusion())
                .advice(d.getAdvice())
                .status(d.getStatus())
                .superseded(d.getSuperseded())
                .analyzedBy(d.getAnalyzedBy())
                .analyzedAt(d.getAnalyzedAt())
                .build();
    }

    private ReportAuditDTO.DiscussionView toDiscussionView(ChatMessageDocument d) {
        return ReportAuditDTO.DiscussionView.builder()
                .chatId(d.getChatId())
                .role(d.getRole())
                .content(d.getContent())
                .createTime(d.getCreateTime())
                .build();
    }
}