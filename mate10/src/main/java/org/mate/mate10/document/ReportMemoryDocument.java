package org.mate.mate10.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Document(collection = "report_memory")
public class ReportMemoryDocument {
    @Id
    private String id;

    @Field("report_no")
    private String reportNo;
    //蒸馏后的结论
    @Field("conclusion")
    private String conclusion;
    //改进建议
    @Field("advice")
    private String advice;
    //完整回答
    @Field("full_answer")
    private String fullAnswer;
    //质量
    @Field("status")
    private String status;
    //是否已被更新的结论覆盖(true = 仅留痕,不注入)
    @Field("superseded")
    private Boolean superseded = false;

    @Field("analyzed_by")
    private Long analyzedBy;

    @Field("analyzed_at")
    private LocalDateTime analyzedAt;
    //结论基于的数据指纹
    @Field("data_version")
    private String dataVersion;
    //覆盖原因
    @Field("change_reason")
    private String changeReason;
}
