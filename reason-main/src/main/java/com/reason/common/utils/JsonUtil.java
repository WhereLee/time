package com.reason.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON 工具类（fastjson2 实现，统一替换原 fastjson1 + codehaus Jackson1 混合实现）
 * 方法签名保持兼容，调用方无感知
 */
public class JsonUtil {
  private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);
  
  @Deprecated
  public static Map<String, Object> toMap(String jsonStr) {
    Map<String, Object> argsMap;
    if (StringUtils.isEmpty(jsonStr)) {
      return new HashMap<>();
    }
    try {
      argsMap = JSONObject.parseObject(jsonStr, Map.class);
    } catch (Exception ex) {
      if (jsonStr.contains("\\")) {
        try {
          jsonStr = jsonStr.replace("\\", "\\\\");
          argsMap = JSONObject.parseObject(jsonStr, Map.class);
        } catch (Exception e) {
          logger.error("JSON转Map出错，jsonStr=" + jsonStr, e);
          argsMap = new HashMap<>();
        } 
      } else {
        logger.error("JSON转Map出错，jsonStr=" + jsonStr, ex);
        argsMap = new HashMap<>();
      } 
    } 
    return argsMap;
  }
  
  public static Map<String, Object> toMapFromJsonStr(Object obj) {
    return toMapFromJsonStr(StringUtils.toStringNotNull(obj));
  }
  
  public static Map<String, Object> toMapFromJsonStr(String jsonStr) {
    Map<String, Object> argsMap;
    if (StringUtils.isEmpty(jsonStr)) {
      return new HashMap<>();
    }
    try {
      argsMap = JSONObject.parseObject(jsonStr, Map.class);
    } catch (Exception ex) {
      if (jsonStr.contains("\\")) {
        try {
          jsonStr = jsonStr.replace("\\", "\\\\");
          argsMap = JSONObject.parseObject(jsonStr, Map.class);
        } catch (Exception e) {
          logger.error("JSON转Map出错，jsonStr=" + jsonStr, e);
          argsMap = new HashMap<>();
        } 
      } else {
        logger.error("JSON转Map出错，jsonStr=" + jsonStr, ex);
        argsMap = new HashMap<>();
      } 
    } 
    return argsMap;
  }
  
  public static Map<String, Object> toMapFromJsonStrSort(String jsonStr) {
    return toMapFromJsonStr(jsonStr);
  }
  
  @Deprecated
  public static String toJsonString(Object obj) {
    if (obj == null || "".equals(obj)) {
      return "";
    }
    try {
      return JSON.toJSONString(obj);
    } catch (Exception e) {
      logger.error("Json转对象出错，obj=" + obj, e);
      return null;
    } 
  }
  
  public static String toJsonFromObject(Object obj) {
    if (obj == null || "".equals(obj)) {
      return "";
    }
    try {
      return JSON.toJSONString(obj, JSONWriter.Feature.WriteNullStringAsEmpty, JSONWriter.Feature.WriteNullBooleanAsFalse);
    } catch (Exception e) {
      logger.error("Json转对象出错，obj=" + obj, e);
      return null;
    } 
  }
  
  public static String toJsonFromObjectMapNull(Object obj) {
    if (obj == null || "".equals(obj)) {
      return "";
    }
    try {
      return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullStringAsEmpty, JSONWriter.Feature.WriteNullBooleanAsFalse);
    } catch (Exception e) {
      logger.error("Json转对象出错，obj=" + obj, e);
      return null;
    } 
  }
  
  public static String toSqlJsonFromObject(Object obj) {
    String sqlStr = toJsonFromObject(obj);
    return (sqlStr == null) ? null : sqlStr.replaceAll("\\\\", "\\\\\\\\");
  }
  
  @Deprecated
  public static <T> T fromString(String jsonStr, Class<T> c) {
    if (StringUtils.isEmpty(jsonStr)) {
      return null;
    }
    try {
      return JSONObject.parseObject(jsonStr, c);
    } catch (Exception ex) {
      if (jsonStr.contains("\\")) {
        try {
          jsonStr = jsonStr.replace("\\", "\\\\");
          return JSONObject.parseObject(jsonStr, c);
        } catch (Exception e) {
          logger.error("JSON转对象出错，jsonStr=" + jsonStr, e);
        } 
      } else {
        logger.error("JSON转对象出错，jsonStr=" + jsonStr, ex);
      } 
      return null;
    } 
  }
  
  public static <T> T toBeanFromStr(String jsonStr, Class<T> c) {
    if (StringUtils.isEmpty(jsonStr)) {
      return null;
    }
    try {
      return JSONObject.parseObject(jsonStr, c);
    } catch (Exception ex) {
      if (jsonStr.contains("\\")) {
        try {
          jsonStr = jsonStr.replace("\\", "\\\\");
          return JSONObject.parseObject(jsonStr, c);
        } catch (Exception e) {
          logger.error("JSON转对象出错，jsonStr=" + jsonStr, e);
        } 
      } else {
        logger.error("JSON转对象出错，jsonStr=" + jsonStr, ex);
      } 
      return null;
    } 
  }
  
  public static <T> T toBeanFromStrSort(String jsonStr, Class<T> c) {
    return toBeanFromStr(jsonStr, c);
  }
  
  public static <T> List<T> toList(Object obj, Class<T> c) {
    return (obj instanceof String) ? toList((String)obj, c) : toList(toJsonFromObject(obj), c);
  }
  
  public static <T> List<T> toList(String jsonStr, Class<T> c) {
    if (StringUtils.isEmpty(jsonStr)) {
      return null;
    }
    try {
      return (List<T>) (List<?>) JSON.parseArray(jsonStr, c);
    } catch (Exception ex) {
      if (jsonStr.contains("\\")) {
        try {
          jsonStr = jsonStr.replace("\\", "\\\\");
          return (List<T>) (List<?>) JSON.parseArray(jsonStr, c);
        } catch (Exception e) {
          logger.error("JSON转List出错，jsonStr=" + jsonStr, e);
        } 
      } else {
        logger.error("JSON转List出错，jsonStr=" + jsonStr, ex);
      } 
      return null;
    } 
  }
  
  public static <T> List<T> toMapList(String jsonStr, Class<Map> c) {
    if (StringUtils.isEmpty(jsonStr)) {
      return null;
    }
    try {
      return (List<T>) (List<?>) JSON.parseArray(jsonStr, c);
    } catch (Exception ex) {
      if (jsonStr.contains("\\")) {
        try {
          jsonStr = jsonStr.replace("\\", "\\\\");
          return (List<T>) (List<?>) JSON.parseArray(jsonStr, c);
        } catch (Exception e) {
          logger.error("JSON转List出错，jsonStr=" + jsonStr, e);
        } 
      } else {
        logger.error("JSON转List出错，jsonStr=" + jsonStr, ex);
      } 
      return null;
    } 
  }
  
  public static List<Map<String, Object>> toMapListForObj(Object obj) {
    String jsonStr = toJsonFromObject(obj);
    if (StringUtils.isEmpty(jsonStr)) {
      return new ArrayList<>();
    }
    try {
      return JSONObject.parseObject(jsonStr, new com.alibaba.fastjson2.TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception ex) {
      logger.error("JSON转List出错，jsonStr=" + jsonStr, ex);
      return new ArrayList<>();
    }
  }

  public static <T> List<T> toListFromJsonStr(String jsonStr) {
    List<T> argsMap;
    if (StringUtils.isEmpty(jsonStr)) {
      return new ArrayList<>();
    }
    try {
      argsMap = (List<T>) (List<?>) JSON.parseArray(jsonStr, Object.class);
    } catch (Exception ex) {
      if (jsonStr.contains("\\")) {
        try {
          jsonStr = jsonStr.replace("\\", "\\\\");
          return (List<T>) (List<?>) JSON.parseArray(jsonStr, Object.class);
        } catch (Exception e) {
          logger.error("JSON转List出错，jsonStr=" + jsonStr, e);
        } 
      } else {
        logger.error("JSON转List出错，jsonStr=" + jsonStr, ex);
      } 
      return new ArrayList<>();
    } 
    return argsMap;
  }

  public static <T> List<T> toListFromJsonStrSort(String jsonStr) {
    return toListFromJsonStr(jsonStr);
  }
}