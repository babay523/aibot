package com.atome.bot.service;

import com.atome.bot.model.MilvusVectorRow;
import com.atome.bot.model.OverrideVectorRow;
import com.atome.bot.model.SearchHit;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MilvusService {

    private final MilvusClient milvusClient;

    @Value("${milvus.collection.kb-chunks}")
    private String kbChunksCollection;

    @Value("${milvus.collection.overrides}")
    private String overridesCollection;

    @Value("${milvus.metric}")
    private String metricType;

    private int dimension = -1; // 会在初始化时探测

    public MilvusService(MilvusClient milvusClient) {
        this.milvusClient = milvusClient;
    }

    /**
     * 设置维度（从Ollama探测后传入）
     */
    public void setDimension(int dim) {
        this.dimension = dim;
    }

    /**
     * 确保集合存在，如果不存在则创建
     */
    public void ensureCollections(int dim) {
        this.dimension = dim;

        ensureCollection(kbChunksCollection, Arrays.asList(
                FieldType.newBuilder()
                        .withName("chunk_id")
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(false)
                        .build(),
                FieldType.newBuilder()
                        .withName("embedding")
                        .withDataType(DataType.FloatVector)
                        .withDimension(dim)
                        .build(),
                FieldType.newBuilder()
                        .withName("article_id")
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName("source_id")
                        .withDataType(DataType.Int32)
                        .build()
        ));

        ensureCollection(overridesCollection, Arrays.asList(
                FieldType.newBuilder()
                        .withName("override_id")
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(false)
                        .build(),
                FieldType.newBuilder()
                        .withName("embedding")
                        .withDataType(DataType.FloatVector)
                        .withDimension(dim)
                        .build(),
                FieldType.newBuilder()
                        .withName("active")
                        .withDataType(DataType.Bool)
                        .build(),
                FieldType.newBuilder()
                        .withName("chosen_url")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(1024)
                        .build()
        ));
    }

    private void ensureCollection(String collectionName, List<FieldType> fieldTypes) {
        // 检查集合是否存在
        R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collectionName).build()
        );

        if (Boolean.TRUE.equals(hasCollection.getData())) {
            // 检查维度是否匹配
            R<DescribeCollectionResponse> desc = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder().withCollectionName(collectionName).build()
            );
            // 如果维度不匹配，需要删除重建
            int existingDim = getDimensionFromCollection(desc.getData(), collectionName);
            if (existingDim != dimension) {
                milvusClient.dropCollection(
                        DropCollectionParam.newBuilder().withCollectionName(collectionName).build()
                );
                createCollection(collectionName, fieldTypes);
            }
        } else {
            createCollection(collectionName, fieldTypes);
        }
    }

    private int getDimensionFromCollection(DescribeCollectionResponse response, String collectionName) {
        for (var field : response.getSchema().getFieldsList()) {
            if (field.getName().equals("embedding")) {
                return field.getTypeParamsList().stream()
                        .filter(p -> p.getKey().equals("dim"))
                        .findFirst()
                        .map(p -> Integer.parseInt(p.getValue()))
                        .orElse(-1);
            }
        }
        return -1;
    }

    private void createCollection(String collectionName, List<FieldType> fieldTypes) {
        // 创建集合
        milvusClient.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldTypes(fieldTypes)
                .withShardsNum(2)
                .build());

        // 创建索引
        milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("embedding")
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"M\":16,\"efConstruction\":200}")
                .build());

        // 加载集合到内存
        milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
    }

    /**
     * 批量插入 KB chunks
     */
    public void insertKbChunks(List<MilvusVectorRow> rows) {
        if (rows == null || rows.isEmpty()) return;

        List<Long> chunkIds = rows.stream().map(MilvusVectorRow::getChunkId).collect(Collectors.toList());
        List<List<Float>> embeddings = rows.stream().map(MilvusVectorRow::getEmbedding).collect(Collectors.toList());
        List<Long> articleIds = rows.stream().map(MilvusVectorRow::getArticleId).collect(Collectors.toList());
        List<Integer> sourceIds = rows.stream().map(MilvusVectorRow::getSourceId).collect(Collectors.toList());

        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field("chunk_id", chunkIds),
                new InsertParam.Field("embedding", embeddings),
                new InsertParam.Field("article_id", articleIds),
                new InsertParam.Field("source_id", sourceIds)
        );

        milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(kbChunksCollection)
                .withFields(fields)
                .build());

        // 刷新
        milvusClient.flush(FlushParam.newBuilder()
                .withCollectionNames(Collections.singletonList(kbChunksCollection))
                .build());
    }

    /**
     * 按 sourceId 删除 KB chunks
     */
    public void deleteKbBySourceId(Integer sourceId) {
        milvusClient.delete(DeleteParam.newBuilder()
                .withCollectionName(kbChunksCollection)
                .withExpr("source_id == " + sourceId)
                .build());

        milvusClient.flush(FlushParam.newBuilder()
                .withCollectionNames(Collections.singletonList(kbChunksCollection))
                .build());
    }

    /**
     * 搜索 KB chunks，返回 chunk hits（需在外层聚合到 article）
     */
    public List<SearchHit> searchKb(List<Float> queryVector, int topK) {
        List<String> search_output_fields = Arrays.asList("chunk_id", "article_id", "source_id");

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(kbChunksCollection)
                .withMetricType(MetricType.COSINE)
                .withVectorFieldName("embedding")
                .withTopK(topK)
                .withVectors(Collections.singletonList(queryVector))
                .withParams("{\"ef\":128}")
                .withOutFields(search_output_fields)
                .build();

        R<SearchResults> results = milvusClient.search(searchParam);
        
        // 检查搜索结果
        if (results.getStatus() != 0) {
            System.err.println("⚠️ Milvus 搜索失败: code=" + results.getStatus() + ", msg=" + results.getMessage());
            return new ArrayList<>();
        }
        
        if (results.getData() == null || results.getData().getResults() == null) {
            System.err.println("⚠️ Milvus 搜索返回空结果");
            return new ArrayList<>();
        }
        
        SearchResultsWrapper wrapper = new SearchResultsWrapper(results.getData().getResults());

        List<SearchHit> hits = new ArrayList<>();
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
        List<QueryResultsWrapper.RowRecord> rowRecords = wrapper.getRowRecords(0);
        
        System.out.println("🔍 Milvus 搜索结果: " + rowRecords.size() + " 条记录");
        
        for (int i = 0; i < rowRecords.size(); i++) {
            QueryResultsWrapper.RowRecord record = rowRecords.get(i);
            
            // 安全地解析字段（处理字符串或数值类型）
            Long chunkId = parseLong(record.get("chunk_id"));
            Long articleId = parseLong(record.get("article_id"));
            Integer sourceId = parseInt(record.get("source_id"));
            float score = idScores.get(i).getScore();
            
            System.out.println("  Record " + i + ": chunkId=" + chunkId + ", articleId=" + articleId + ", score=" + score);
            
            hits.add(new SearchHit(chunkId, articleId, score, sourceId));
        }
        return hits;
    }

    /**
     * 插入 Override
     */
    public void insertOverride(OverrideVectorRow row) {
        List<Long> overrideIds = Collections.singletonList(row.getOverrideId());
        List<List<Float>> embeddings = Collections.singletonList(row.getEmbedding());
        List<Boolean> actives = Collections.singletonList(row.getActive());
        List<String> chosenUrls = Collections.singletonList(row.getChosenUrl());

        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field("override_id", overrideIds),
                new InsertParam.Field("embedding", embeddings),
                new InsertParam.Field("active", actives),
                new InsertParam.Field("chosen_url", chosenUrls)
        );

        milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(overridesCollection)
                .withFields(fields)
                .build());

        milvusClient.flush(FlushParam.newBuilder()
                .withCollectionNames(Collections.singletonList(overridesCollection))
                .build());
    }

    /**
     * 搜索 Override（仅 active=true），返回 top1 匹配（含相似度分数）
     */
    public OverrideHit searchOverrideHit(List<Float> queryVector) {
        List<String> search_output_fields = Arrays.asList("override_id", "active", "chosen_url");

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(overridesCollection)
                .withMetricType(MetricType.COSINE)
                .withVectorFieldName("embedding")
                .withTopK(1)
                .withVectors(Collections.singletonList(queryVector))
                .withParams("{\"ef\":128}")
                .withOutFields(search_output_fields)
                .withExpr("active == true")
                .build();

        R<SearchResults> results = milvusClient.search(searchParam);
        SearchResultsWrapper wrapper = new SearchResultsWrapper(results.getData().getResults());

        List<QueryResultsWrapper.RowRecord> rowRecords = wrapper.getRowRecords(0);
        if (rowRecords.isEmpty()) {
            return null;
        }

        QueryResultsWrapper.RowRecord record = rowRecords.get(0);
        Long overrideId = (Long) record.get("override_id");
        Boolean active = (Boolean) record.get("active");
        String chosenUrl = (String) record.get("chosen_url");

        float score = wrapper.getIDScore(0).get(0).getScore();

        if (active == null || !active) {
            return null;
        }

        return new OverrideHit(overrideId, chosenUrl, score);
    }

    public record OverrideHit(Long overrideId, String chosenUrl, float score) {}

    /**
     * 辅助方法：安全地将 Object 解析为 Long
     */
    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return Long.parseLong(value.toString());
    }

    /**
     * 辅助方法：安全地将 Object 解析为 Integer
     */
    private Integer parseInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) return Integer.parseInt((String) value);
        return Integer.parseInt(value.toString());
    }

    /**
     * 更新 Override active 状态（删除旧记录，插入新记录）
     * Milvus 不支持直接更新，采用 delete + insert 策略
     */
    public void updateOverrideActive(Long overrideId, boolean active, List<Float> embedding, String chosenUrl) {
        // 删除旧记录
        milvusClient.delete(DeleteParam.newBuilder()
                .withCollectionName(overridesCollection)
                .withExpr("override_id == " + overrideId)
                .build());

        // 插入新记录（更新 active 状态）
        if (embedding != null && !embedding.isEmpty()) {
            insertOverride(new OverrideVectorRow(overrideId, embedding, active, chosenUrl));
        }
    }
}
