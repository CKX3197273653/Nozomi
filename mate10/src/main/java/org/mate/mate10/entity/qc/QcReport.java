package org.mate.mate10.entity.qc;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("qc_report")
public class QcReport {
    @TableId(type = IdType.AUTO)
    private  Long id;
    //业务字段
    private String reportNo;
    private String productName;
    private String productBatch;
    private String productionLine;
    private String inspector;
    private LocalDateTime inspectTime;
    //文件信息
    private String fileUrl;
    private String fileType;
    private String status;
    private Integer totalDefects;
    private String severityLevel;
    // 时间字段
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    //归档 MP在所有CRUD上自动补deleted = 0
    @TableLogic
    private Integer deleted;
   //归档时间
    private LocalDateTime archiveTime;
    //归档操作人
    private Long archivedBy;

    //不落库
    @TableField(exist = false)
    private Boolean fileExists;
}
