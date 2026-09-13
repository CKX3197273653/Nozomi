package org.mate.mate10.service;

import java.util.List;

public interface VectorService {
    //判断集合has状态
    boolean hasCollection(String collName);

    //创建集合
    void createCollection(String collName,int dim);

    //给向量字段构建索引
    void createIndex(String collName);

    //加载进内存
    void loadCollection(String collName);

    //新增
    Long insert(String collName, List<Long> idList,List<List<Float>> vectorList);

    //删除
//    long deleteCollection(String collName, List<Long> idList);

    //相似度检索
    List<List<Long>> searchSimilar(String collName,List<List<Float>> targetVectors,int topK);

    //id查
    List<List<Object>> queryByIds(String collName,List<Long> idList);

    //更新
    Long updateById(String collName, Long id, List<Float> newVector);

    //删除整个集合
    void dropCollection(String collName);

    long deleteByIds(String collName, List<Long> idList);
}
