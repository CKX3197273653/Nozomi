package org.mate.mate10.entity.qc;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
}
