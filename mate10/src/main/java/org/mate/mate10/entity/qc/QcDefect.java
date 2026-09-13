package org.mate.mate10.entity.qc;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("qc_defect")
public class QcDefect {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;
    private String defectType;
    private String defectPosition;
    private String severity;
    private String description;
    private BigDecimal confidence;
    private Integer isConfirmed;

    private LocalDateTime createTime;
}
