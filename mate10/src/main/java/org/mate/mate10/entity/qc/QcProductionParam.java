package org.mate.mate10.entity.qc;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("qc_production_param")
public class QcProductionParam {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;
    private BigDecimal temperature;
    private BigDecimal pressure;
    private BigDecimal humidity;
    private BigDecimal speed;
    private String operator;
    private String remark;

    private LocalDateTime createTime;
}
