package org.mate.mate10.mapper.qc;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.mate.mate10.entity.qc.QcProductionParam;


@Mapper
public interface QcProductionParamMapper extends BaseMapper<QcProductionParam> {
    @Select("SELECT * FROM qc_production_param WHERE report_id = #{reportId}")
    QcProductionParam selectByReportId(Long reportId);
}
