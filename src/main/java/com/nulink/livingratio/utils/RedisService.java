package com.nulink.livingratio.utils;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Service
public interface RedisService {

    /**
     * set
     * @param key
     * @param value
     * @param time
     */
    void set(String key, Object value, long time);

    /**
     * set
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    void set(String key, String value, long time, TimeUnit unit);

    /**
     * set
     * @param key
     * @param value
     *
     */
    void set(String key, Object value);


    /**
     * get
     * @param key
     * @return object
     */
    Object get(String key);

    Boolean setNx(String key, String value);

    Boolean setNx(String key, String value, long time, TimeUnit unit);

    /**
     * delete
     */
    Boolean del(String key);

    /**
     * delete list
     */
    Long del(List<String> keys);

    /**
     * Set expiration time
     */
    Boolean expire(String key, long time);

    /**
     * Get expiration time
     */
    Long getExpire(String key);

    /**
     * Check if the key exists.
     */
    Boolean hasKey(String key);

    /**
     * Increment by delta
     */
    Long incr(String key, long delta);

    /**
     * Decrement by delta
     */
    Long decr(String key, long delta);

    /**
     * Retrieve the attribute in the Hash structure
     */
    Object hGet(String key, String hashKey);

    /**
     * Put an attribute into the Hash structure
     */
    Boolean hSet(String key, String hashKey, Object value, long time);

    /**
     * Put an attribute into the Hash structure
     */
    void hSet(String key, String hashKey, Object value);

    /**
     * Retrieve the entire Hash structure directly
     */
    Map<Object, Object> hGetAll(String key);

    /**
     * Retrieve the entire Hash structure directly
     */
    Boolean hSetAll(String key, Map<String, Object> map, long time);

    /**
     * Retrieve the entire Hash structure directly
     */
    void hSetAll(String key, Map<String, Object> map);

    /**
     * Remove an attribute from the Hash structure
     */
    void hDel(String key, Object... hashKey);

    /**
     * Check if the attribute exists in the Hash structure
     */
    Boolean hHasKey(String key, String hashKey);

    /**
     * Increment the attribute in the Hash structure
     */
    Long hIncr(String key, String hashKey, Long delta);

    /**
     * Decrement the attribute in the Hash structure
     */
    Long hDecr(String key, String hashKey, Long delta);

    /**
     * get the Set structure
     */
    Set<Object> sMembers(String key);

    /**
     * Add an element to the Set structure
     */
    Long sAdd(String key, Object... values);

    /**
     * Add an element to the Set structure
     */
    Long sAdd(String key, long time, Object... values);

    /**
     * Check if it is an attribute in the Set
     */
    Boolean sIsMember(String key, Object value);

    /**
     * Retrieve the length of the Set structure
     */
    Long sSize(String key);

    /**
     * Remove an attribute from the Set structure
     */
    Long sRemove(String key, Object... values);

    /**
     * get an element from the List structure
     */
    List<Object> lRange(String key, long start, long end);

    /**
     * get the length of the List structure
     */
    Long lSize(String key);

    /**
     * get an attribute from the List structure based on an index
     */
    Object lIndex(String key, long index);

    /**
     * Add an attribute to the List structure
     */
    Long lPush(String key, Object value);

    /**
     * Append an attribute to the List structure
     */
    Long lPush(String key, Object value, long time);

    /**
     * Bulk add attributes to the List structure
     */
    Long lPushAll(String key, Object... values);

    /**
     * Bulk add attributes to the List structure
     */
    Long lPushAll(String key, Long time, Object... values);

    /**
     * Remove an attribute from the List structure
     */
    Long lRemove(String key, long count, Object value);

}
