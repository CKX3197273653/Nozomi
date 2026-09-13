package org.mate.mate10.service.Impl;



import io.milvus.grpc.*;
import io.milvus.client.MilvusClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import org.mate.mate10.service.VectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

@Service
public class VectorServiceImpl implements VectorService {

    @Autowired
    private MilvusClient milvusClient;

    @Override
    public boolean hasCollection(String collName) {
        HasCollectionParam param = HasCollectionParam.newBuilder()
                .withCollectionName(collName)
                .build();
        R<Boolean> r = milvusClient.hasCollection(param);
        return r.getData();
    }

    @Override
    public void createCollection(String collName, int dim) {
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
        //向量字段
            FieldType vector = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(dim)
                .build();
        CreateCollectionParam create = CreateCollectionParam.newBuilder()
                .withCollectionName(collName)
                .withFieldTypes(Arrays.asList(idField, vector))
                .build();
        milvusClient.createCollection(create);
    }

    @Override
    public void createIndex(String collName) {
        CreateIndexParam index = CreateIndexParam.newBuilder()
                .withCollectionName(collName)
                .withFieldName("vector")
                .withIndexType(IndexType.FLAT)
                /*
                * FLAT:暴力索引,高精准度,会逐条计算全部向量索引
                * IVF_FLAT:聚类分桶索引,速度很快
                * HNSW:图结构索引,检索速度最快,内存占用高
                * IVF_SQ8:向量8bit索引,量化压缩,适合海量数据
                * */
                .withMetricType(MetricType.L2)
                /*
                * MetricType为评判标准;IndexType为查找方式
                * L2（欧式距离):两点之间,数值越小->向量越相似
                * IP(内积):数值越大->向量越相似
                * COSINE(余弦相似度):衡量向量夹角,夹角越小越相似
                * */
                .build();
        milvusClient.createIndex(index);
    }

    @Override
    //milvus默认数据存在硬盘.索引文件在磁盘,实现方法将他们读到服务器内存中
    //向量中的search必须依赖内存中的索引才可以高速查询
    public void loadCollection(String collName) {
        LoadCollectionParam load = LoadCollectionParam.newBuilder()
                .withCollectionName(collName)
                .build();
        milvusClient.loadCollection(load);
    }

    @Override
    public Long insert(String collName, List<Long> idList, List<List<Float>> vectorList) {
        //id是Long类型主键编号,vector是FloatVector向量数组,插入必须传入两组数据
        //idList [0] 和vectorList [0],组成一条完整数据
        InsertParam.Field idFiled = new InsertParam.Field("id", idList);
        InsertParam.Field vectorFiled = new InsertParam.Field("vector", vectorList);
        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collName)
                .withFields(Arrays.asList(idFiled, vectorFiled))
                .build();

        R<MutationResult> insertWrap = milvusClient.insert(insertParam);
        if (!insertWrap.getStatus().equals(R.Status.Success.getCode())) {
            throw new RuntimeException("插入向量失败：" + insertWrap.getMessage());
        }
        MutationResult result = insertWrap.getData();
        return result.getInsertCnt();
    }

//    @Override
//    public long deleteCollection(String collName, List<Long> idList) {
//        String expr = "id in " + idList.toString();
//        DeleteParam deleteParam = DeleteParam.newBuilder()
//                .withCollectionName(collName)
//                .withExpr(expr)
//                .build();
//        R<MutationResult> respR = milvusClient.delete(deleteParam);
//        if (!respR.getStatus().equals(R.Status.Success.getCode())) {
//            throw new RuntimeException("删除失败：" + respR.getMessage());
//        }
//        MutationResult deleteResp = respR.getData();
//        return deleteResp.getDeleteCnt();
//    }


    @Override
    public long deleteByIds(String collName, List<Long> idList) {
        String expr = "id in " + idList.toString();
        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(collName)
                .withExpr(expr)
                .build();
        R<MutationResult> respR = milvusClient.delete(deleteParam);
        if (!respR.getStatus().equals(R.Status.Success.getCode())) {
            throw new RuntimeException("删除失败：" + respR.getMessage());
        }
        MutationResult deleteResp = respR.getData();
        return deleteResp.getDeleteCnt();
    }

//    public List<List<Object>> searchAndQueryVector(String collName,List<List<Float>> targetVectors, int topK) {
//        List<List<Object>> finalResult = new ArrayList<>();
//        //调用searchSimilar 拿到Ids
//        List<List<Long>> searchResults = searchSimilar(collName, targetVectors, topK);
//        //遍历结果
//        for (List<Long> idList : searchResults) {
//            //防空
//            if (idList == null || idList.isEmpty()) { continue;}
//            //用ids调用queryByIds拿vector
//            //queryByIds内部调用parseMilvusQueryResult 解析出 [id,vector]
//            List<List<Object>> queryResult = queryByIds(collName, idList);
//            //把结果合并到最终返回列表
//            finalResult.addAll(queryResult);}
//        return finalResult;
//    }
    private List<List<Long>> parseMilvusSearchResult(SearchResults searchResults) {
        List<List<Long>> outer = new ArrayList<>();
        //防空
        if (searchResults == null || !searchResults.hasResults()) {
            return outer;
        }
        //结果数据
        SearchResultData results = searchResults.getResults();
        //结果提取ids
        IDs ids = results.getIds();
        LongArray longArray = ids.getIntId();
        List<Long> idList = longArray.getDataList();
        //加入外层列表返回
        outer.add(idList);
        return outer;
    }


    private List<List<Object>> parseMilvusQueryResult(QueryResults queryResults) {
        List<List<Object>> result = new ArrayList<>();

        if (queryResults == null) {
            return result;
        }

        List<FieldData> fieldData = queryResults.getFieldsDataList();
        LongArray idLongArray = null;
        VectorArray vectorArray = null;

        for (FieldData field : fieldData) {
            if ("id".equals(field.getFieldName())) {
                idLongArray = field.getScalars().getLongData();
            }
            if ("vector".equals(field.getFieldName())) {
                vectorArray = field.getVectors().getVectorArray();
            }
        }

        if (idLongArray == null || vectorArray == null) {
            return result;
        }

        List<Long> idList = idLongArray.getDataList();
        List<VectorField> vectorDataList = vectorArray.getDataList();
        int minSize = Math.min(idList.size(), vectorDataList.size());
        for (int i = 0; i < minSize; i++) {
            List<Object> row = new ArrayList<>();
            row.add(idList.get(i));
            row.add(vectorDataList.get(i).getFloatVector().getDataList());
            result.add(row);
        }

        return result;
    }

    @Override
    //向量相似度检索,
    public List<List<Long>> searchSimilar(String collName, List<List<Float>> targetVectors, int topK) {
        SearchParam search = SearchParam.newBuilder()
                .withCollectionName(collName)        //集合名
                .withVectorFieldName("vector")       //向量字段
                .withVectors(targetVectors)          //待检索向量组
                .withTopK(topK)                      //topK
                .withMetricType(MetricType.L2)       //距离度量方式L2
                .build();
       //统一泛型返回封装类
        R<SearchResults> searchWrap = milvusClient.search(search);
        if (!searchWrap.getStatus().equals(R.Status.Success.getCode())) {
            throw new RuntimeException("向量检索失败：" + searchWrap.getMessage());}
        SearchResults data = searchWrap.getData();
        return parseMilvusSearchResult(data);
    }

    @Override
    public List<List<Object>> queryByIds(String collName, List<Long> idList) {
        String expr = "id in " + idList.toString();
        QueryParam query = QueryParam.newBuilder()
                .withCollectionName(collName)
                .withExpr(expr)
                .addOutField("id")
                .addOutField("vector")
                .build();

        R<QueryResults> queryWrap = milvusClient.query(query);
        if (!queryWrap.getStatus().equals(R.Status.Success.getCode())) {
            throw new RuntimeException("按ID查询失败：" + queryWrap.getMessage());
        }
        QueryResults queryData =  queryWrap.getData();
        return parseMilvusQueryResult(queryData);
    }

    @Override
    public Long updateById(String collName, Long id, List<Float> newVector) {
        // 先删
        deleteByIds(collName, List.of(id));
        // 再插
        return insert(collName, List.of(id), List.of(newVector));
    }

    @Override
    public void dropCollection(String collName) {
        DropCollectionParam drop = DropCollectionParam.newBuilder()
                .withCollectionName(collName)
                .build();
        milvusClient.dropCollection(drop);
    }
}
