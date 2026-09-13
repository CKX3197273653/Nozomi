package org.mate.mate10.service.Impl.qc;

import org.mate.mate10.entity.qc.QcProductionParam;
import org.mate.mate10.mapper.qc.QcProductionParamMapper;
import org.mate.mate10.service.qc.QcProductionParamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QcProductionParamServiceImpl implements QcProductionParamService {
    @Autowired
    private QcProductionParamMapper qcProductionParamMapper;

    @Override
    public Long addParam(QcProductionParam param) {
        qcProductionParamMapper.insert(param);
        return param.getId();
    }

    @Override
    public boolean updateParam(QcProductionParam param) {
        return qcProductionParamMapper.updateById(param) > 0;
    }

    @Override
    public QcProductionParam getByReportId(Long reportId) {
        return qcProductionParamMapper.selectByReportId(reportId);
    }
}
