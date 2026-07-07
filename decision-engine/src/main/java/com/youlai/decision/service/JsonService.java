package com.youlai.decision.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JsonService {

    private final ObjectMapper objectMapper;

    /**
     * 创建 JSON 工具服务。
     *
     * @param objectMapper Spring Boot 自动配置的 Jackson 对象映射器
     */
    public JsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 序列化失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 将 JSON 字符串解析为有序 Map。
     *
     * @param json JSON 字符串
     * @return 解析后的键值结构
     */
    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 解析失败: " + ex.getMessage(), ex);
        }
    }

    public List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 解析失败: " + ex.getMessage(), ex);
        }
    }

    public List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 解析失败: " + ex.getMessage(), ex);
        }
    }
}
