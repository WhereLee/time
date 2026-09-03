/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.common.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类
 *
 * @author Mark sunlightcs@gmail.com
 */
@Component
public class RedisUtils {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ValueOperations<String, String> valueOperations;
    @Autowired
    private HashOperations<String, String, Object> hashOperations;
    @Autowired
    private ListOperations<String, Object> listOperations;
    @Autowired
    private SetOperations<String, Object> setOperations;
    @Autowired
    private ZSetOperations<String, Object> zSetOperations;
    /**  默认过期时长，单位：秒 */
    public final static long DEFAULT_EXPIRE = 60 * 60 * 24;
    /**  不设置过期时长 */
    public final static long NOT_EXPIRE = -1;

    public void set(String key, Object value, long expire){
        valueOperations.set(key, toJson(value));
        if(expire != NOT_EXPIRE){
            redisTemplate.expire(key, expire, TimeUnit.SECONDS);
        }
    }

    public void set(String key, Object value){
        set(key, value, DEFAULT_EXPIRE);
    }

    public <T> T get(String key, Class<T> clazz, long expire) {
        String value = valueOperations.get(key);
        if(expire != NOT_EXPIRE){
            redisTemplate.expire(key, expire, TimeUnit.SECONDS);
        }
        return value == null ? null : fromJson(value, clazz);
    }

    public <T> T get(String key, Class<T> clazz) {
        return get(key, clazz, NOT_EXPIRE);
    }

    public String get(String key, long expire) {
        String value = valueOperations.get(key);
        if(expire != NOT_EXPIRE){
            redisTemplate.expire(key, expire, TimeUnit.SECONDS);
        }
        return value;
    }

    public String get(String key) {
        return get(key, NOT_EXPIRE);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * Object转成JSON数据
     */
    private String toJson(Object object){
        if(object instanceof Integer || object instanceof Long || object instanceof Float ||
                object instanceof Double || object instanceof Boolean || object instanceof String){
            return String.valueOf(object);
        }
        return JsonUtil.toJsonString(object);
    }

    /**
     * JSON数据，转成Object
     */
    private <T> T fromJson(String json, Class<T> clazz){
        return JsonUtil.toBeanFromStr(json, clazz);
    }

    /**
     * 判断是否有缓存
     * @param key
     * @param hashKey
     * @return
     */
    public boolean hasHashKey(String key,Object hashKey){
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    /**
     * 取缓存
     * @param key
     * @param hashKey
     * @return
     */
    public Object getHashValue(String key, Object hashKey){
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * 设置缓存
     * @param key
     * @param hashKey
     * @param obj
     */
    public void setHashValue(String key, Object hashKey, Object obj){
        redisTemplate.opsForHash().put(key, hashKey, obj);
    }

    /**
     * 删除缓存
     * @param key
     * @param hashKey
     */
    public void deleteHashValue(String key, Object hashKey) {
        redisTemplate.opsForHash().delete(key, hashKey);
    }

    /**
     * 获取key
     * @param pattern 匹配规则
     * @return
     */
    public Set<String> getKeys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 获取过期时间
     * @param key
     * @return
     */
    public Long getExpire(String key) {
        if (redisTemplate.hasKey(key))
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);

        return null;
    }

    /**
     * 取缓存
     * @param key
     * @return
     */
    public Object getKeyValue(String key) {
        if (redisTemplate.hasKey(key)) {
            return redisTemplate.opsForValue().get(key);
        }

        return null;
    }

    /**
     * 设置缓存
     * @param key
     * @param value
     */
    public void setKeyValue(String key,Object value) {
        redisTemplate.opsForValue().set(key,value);
    }

    /**
     * 设置缓存
     * @param key
     * @param value
     * @param expire 有效时间 单位 秒
     */
    public void setKeyValue(String key,Object value,Long expire) {
        redisTemplate.opsForValue().set(key,value,expire,TimeUnit.SECONDS);
    }

    /**
     * 判断是否有缓存
     * @param key
     * @return
     */
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 删除缓存
     * @param key
     */
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 删除缓存-多个
     * @param keys
     */
    public void deleteKeys(Set<String> keys) {
        redisTemplate.delete(keys);
    }

    /**
     * 删除所有keys
     * @param pattern
     */
    public void deleteAllKeys(String pattern) {
        deleteKeys(getKeys(pattern));
    }
}
