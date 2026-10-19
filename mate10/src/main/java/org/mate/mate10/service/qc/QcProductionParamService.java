package org.mate.mate10.service.qc;

import org.mate.mate10.entity.qc.QcProductionParam;

public interface QcProductionParamService {
    Long addParam(QcProductionParam param);

    boolean updateParam(QcProductionParam param);

    QcProductionParam getByReportId(Long reportId);
    //报告批量删除参数
    int deleteByReportId(Long reportId);}
