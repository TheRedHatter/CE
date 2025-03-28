/*******************************************************************************
 * Copyright 2018 T Mobile, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package com.tmobile.pacman.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tmobile.pacman.common.PacmanSdkConstants;
import com.tmobile.pacman.commons.policy.Annotation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

// TODO: Auto-generated Javadoc
/**
 * The Class ESUtils.
 */
public class ESUtils {

    /** The Constant INPUT_TYPE. */
    private static final String INPUT_TYPE = "input_type";

    /** The Constant CREATE_MAPPING_REQUEST_BODY_TEMPLATE. */
    private static final String CREATE_MAPPING_REQUEST_BODY_TEMPLATE = "  {\"properties\": {\"text\": {\"type\": \"text\",\"analyzer\": \"whitespace\",\"search_analyzer\": \"whitespace\"}}}";

    /** The Constant MAPPING. */
    private static final String MAPPING = "_mapping";

    /** The Constant COUNT. */
    private static final String COUNT = "_count";
    
    /** The Constant QUERY. */
    private static final String QUERY = "query";
    
    /** The Constant logger. */
    private static final Logger logger = LoggerFactory.getLogger(ESUtils.class);

    /**
     * Gets the resources from es.
     *
     * @param index the index
     * @param targetType the target type
     * @param filter the filter
     * @param fields the fields
     * @return the resources from es
     * @throws Exception the exception
     */
    public static List<Map<String, String>> getResourcesFromEs(String index, String targetType,
            Map<String, String> filter, List<String> fields) throws Exception {
        if (Strings.isNullOrEmpty(index) || Strings.isNullOrEmpty(targetType)) {
            throw new Exception("pac_es or targetType cannot be null");
        }
        String url = getEsUrl();
        if (Strings.isNullOrEmpty(url)) {
            throw new Exception("ES_URI not found in the environment variables, do define one end point for ES");
        }

        Map<String, Object> effectiveFilter = new HashMap<>();
        effectiveFilter.putAll(getFilterForType(targetType));
        if (null != filter) {
            effectiveFilter.putAll(filter);
        }
        logger.debug("querying ES for target type:" + targetType);
        Long totalDocs = getTotalDocumentCountForIndexAndType(url, index, targetType, effectiveFilter, null, null);
        logger.debug("total resource count" + totalDocs);
        List<Map<String, String>> details = getDataFromES(url, index.toLowerCase(), targetType.toLowerCase(),
                effectiveFilter, null, null, fields, 0, totalDocs, "_docid");
        return details;
    }

    /**
     * Gets the filter for type.
     *
     * @param targetType the target type
     * @return the filter for type
     */
    private static Map<String, String> getFilterForType(String targetType) {
        Map<String, String> filter = new HashMap<String, String>();
        filter.put("latest", "true"); // this will make sure about the inventory
                                      // we get is latest
        return filter;
    }

    /**
     * Gets the total document count for index and type.
     *
     * @param url the url
     * @param index            name
     * @param type            name
     * @param filter the filter
     * @param mustNotFilter the must not filter
     * @param shouldFilter the should filter
     * @return elastic search count
     */
    @SuppressWarnings("unchecked")
    public static long getTotalDocumentCountForIndexAndType(String url, String index, String type,
            Map<String, Object> filter, Map<String, Object> mustNotFilter, HashMultimap<String, Object> shouldFilter) {

        String urlToQuery = buildURL(url, index, type);

        Map<String, Object> requestBody = new HashMap<String, Object>();
        Map<String, Object> matchFilters = Maps.newHashMap();
        if (type != null) {
            if (filter == null) {
                Map<String, String> typeFilter = new HashMap<String, String>();
                typeFilter.put(PacmanSdkConstants.DOC_TYPE, type);
                matchFilters.put("match", typeFilter);
            } else {
                filter.put(PacmanSdkConstants.DOC_TYPE, type);
                matchFilters.putAll(filter);
            }
        }
        if (null != filter) {
            requestBody.put(QUERY, CommonUtils.buildQuery(matchFilters, mustNotFilter, shouldFilter));
        } else {
            requestBody.put(QUERY, matchFilters);
        }
        String responseDetails = null;
        Gson gson = new GsonBuilder().create();
        try {
            String requestJson = gson.toJson(requestBody, Object.class);
            responseDetails = CommonUtils.doHttpPost(urlToQuery, requestJson,new HashMap<>());
            Map<String, Object> response = (Map<String, Object>) gson.fromJson(responseDetails, Object.class);
            return (long) (Double.parseDouble(response.get("count").toString()));
        } catch (Exception e) {
            logger.error("error getting total documents", e);
            ;
        }
        return -1;
    }

    /**
     * Builds the URL.
     *
     * @param url the url
     * @param index the index
     * @param type the type
     * @return the string
     */
    private static String buildURL(String url, String index, String type) {

        StringBuilder urlToQuery = new StringBuilder(url).append("/").append(index);
//        if (!Strings.isNullOrEmpty(type)) {
//            urlToQuery.append("/").append(type);
//        }
        urlToQuery.append("/").append(COUNT);
        return urlToQuery.toString();
    }

    /**
     * Checks if is valid index.
     *
     * @param url the url
     * @param index the index
     * @return true, if is valid index
     */
    public static boolean isValidIndex(final String url, final String index) {
        String esUrl = new StringBuilder(url).append("/").append(index).toString();
        return CommonUtils.isValidResource(esUrl);
    }

    /**
     * Checks if is valid type.
     *
     * @param url the url
     * @param index the index
     * @param type the type
     * @return true, if is valid type
     */
    public static boolean isValidType(final String url, final String index, final String type) {
        String esUrl = new StringBuilder(url).append("/").append(index).append("/").append(MAPPING).append("/")
                .append(type).toString();
        return CommonUtils.isValidResource(esUrl);
    }

    public static boolean isParentChildRelationExists(final String url, String indexName, String parent, String child) throws IOException {
        // Get the mapping for the index
        String mappingUrl = url + "/" + indexName + "/_mapping";
        String mappingResponse = CommonUtils.doHttpGet(mappingUrl);
        JSONObject mappingObject = new JSONObject(mappingResponse);

        // Get the relations for the index
        JSONObject indexObject = mappingObject.getJSONObject(indexName);
        JSONObject relationsObject = indexObject.getJSONObject("relations");

        // Check if a relation exists between the parent and child entities
        if (relationsObject.has(parent)) {
            JSONArray childArray = relationsObject.getJSONArray(parent);
            for (int i = 0; i < childArray.length(); i++) {
                String relation = childArray.getString(i);
                if (relation.equals(child)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets the es url.
     *
     * @return the es url
     */
    public static String getEsUrl() {
        return CommonUtils.getEnvVariableValue(PacmanSdkConstants.ES_URI_ENV_VAR_NAME);
    }

    /**
     * Creates the mapping.
     *
     * @param esUrl the es url
     * @param index the index
     * @param type the type
     * @return the string
     * @throws Exception the exception
     */
    public static String createMapping(String esUrl, String index, String type) throws Exception {
        String url = new StringBuilder(esUrl).append("/").append(index).append("/").append(MAPPING).append("/")
                .append(type).toString();
        return CommonUtils.doHttpPut(url, CREATE_MAPPING_REQUEST_BODY_TEMPLATE.replace(INPUT_TYPE, type));
    }

    /**
     * Creates the mapping with parent.
     *
     * @param esUrl  the es url
     * @param index  the index
     * @param type   the type
     * @param parent the parent type
     * @return the string
     * @throws Exception the exception
     */
//    public static String createMappingWithParent(String esUrl, String index, String type, String parentType)
//            throws Exception {
//        String url = new StringBuilder(esUrl).append("/").append(index).append("/").append(MAPPING).append("/")
//                .append(type).toString();
//        String requestBody = "  {\"_parent\": { \"type\": \"" + parentType + "\"}}";
//        // String requestBody =
//        // "{\"mappings\":{\"input_type\":{\"dynamic_templates\":[{\"notanalyzed\":{\"match\":\"*\",\"match_mapping_type\":\"string\",\"mapping\":{\"type\":\"string\",\"index\":\"not_analyzed\"}}}]}}}";
//        return CommonUtils.doHttpPut(url, requestBody);
//    }
    public static String createMappingWithParent(String esUrl, String index, String type, String parent) {
        String endPoint = index + "/_mapping";
        // Get existing children
        Map<String, Object> existingChildren = null;
        try {
            existingChildren = getChildRelations(index, parent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Check if parent already has existing children
        List<String> existingChildTypes;
        if (existingChildren.containsKey(parent)) {
            Object existingChildObj = existingChildren.get(parent);
            if (existingChildObj instanceof String) {
                existingChildTypes = new ArrayList<>();
                existingChildTypes.add((String) existingChildObj);
            } else {
                existingChildTypes = (List<String>) existingChildObj;
            }
        } else {
            existingChildTypes = new ArrayList<>();
        }

        // Add new child
        existingChildTypes.add(type);
        existingChildren.put(parent, existingChildTypes);

        // Create childMap with updated relations
        Map<String, Object> childMap = new HashMap<>();
        childMap.put("type", "join");
        childMap.put("relations", existingChildren);

        Map<String, Object> properties = new HashMap<>();
        properties.put(parent + "_relations", childMap);

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("properties", properties);

        String payLoad = null;
        try {
            payLoad = new ObjectMapper().writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        try {
            return CommonUtils.doHttpPut(endPoint, payLoad);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Helper function to retrieve existing child relations
    private static Map<String, Object> getChildRelations(String index, String parent) throws IOException {
        String endPoint = index + "/_mapping";
        String response = CommonUtils.doHttpGet(endPoint);
        JsonNode node = new ObjectMapper().readTree(response);
        JsonNode properties = node.at("/" + index + "/mappings/properties");
        JsonNode relations = properties.get(parent + "_relations").get("relations");
        return new ObjectMapper().convertValue(relations, Map.class);
    }

    /**
     * Creates the index.
     *
     * @param url       the url
     * @param indexName the index name
     * @throws Exception the exception
     */
    public static void createIndex(String url, String indexName) throws Exception {
        String esUrl = new StringBuilder(url).append("/").append(indexName).toString();
        String payLoad = "{\"settings\": { \"number_of_shards\" : 1,\"number_of_replicas\" : 1 }}";
        CommonUtils.doHttpPut(esUrl, payLoad);
    }

    /**
     * Ensure index and type for annotation.
     *
     * @param annotation the annotation
     * @param createIndexIfNotFound the create index if not found
     * @throws Exception the exception
     */
    public static void ensureIndexAndTypeForAnnotation(Annotation annotation, Boolean createIndexIfNotFound)
            throws Exception {
        String esUrl = getEsUrl();
        if(Strings.isNullOrEmpty(esUrl)){
            throw new Exception("ES host cannot be null");
        }
        String indexName = buildIndexNameFromAnnotation(annotation);

        if (!Strings.isNullOrEmpty(indexName)) {
            indexName = indexName.toLowerCase();
        } else
            throw new Exception("Index/datasource/pac_ds name cannot be null or blank");

        if (!isValidIndex(esUrl, indexName)) {
            // createIndex(esUrl, indexName);
            // DO NOT CREATE INDEX, this responsibility is delegated to pacman
            // cloud discovery, if you will create the index, parent , child
            // relation will be lost
            throw new Exception("Index is not yet ready to publish the data");
        }

        String parentType, type;
        if (!Strings.isNullOrEmpty(annotation.get(PacmanSdkConstants.TARGET_TYPE).toString())
                && !Strings.isNullOrEmpty(annotation.get(PacmanSdkConstants.TYPE).toString())) {
            parentType = annotation.get(PacmanSdkConstants.TARGET_TYPE).toString();
            type = getIssueTypeFromAnnotation(annotation);
        } else
            throw new Exception("targetType name cannot be null or blank");

        if (!isValidType(esUrl, indexName, type)) {
            // createMappingWithParent(esUrl, indexName, type,parentType);do not
            // create now, this responsibility is delegated to Inventory
            // collector
            throw new Exception("Index exists but unable to find type to publish the data");
        }

    }


    /**
     * Builds the index name from annotation.
     *
     * @param annotation the annotation
     * @return the string
     */
    public static String buildIndexNameFromAnnotation(final Annotation annotation) {
        return annotation.get(PacmanSdkConstants.DATA_SOURCE_KEY) + "_"
                + annotation.get(PacmanSdkConstants.TARGET_TYPE);
    }

    /**
     * Gets the issue type from annotation.
     *
     * @param annotation the annotation
     * @return the issue type from annotation
     */
    public static String getIssueTypeFromAnnotation(Annotation annotation) {
        return new StringBuilder(annotation.get(PacmanSdkConstants.TYPE).toString()).append("_")
                .append(annotation.get(PacmanSdkConstants.TARGET_TYPE)).toString();
    }

    /**
     * Gets the data from ES.
     *
     * @param url the url
     * @param dataSource the data source
     * @param entityType the entity type
     * @param mustFilter the must filter
     * @param mustNotFilter the must not filter
     * @param shouldFilter the should filter
     * @param fields the fields
     * @param from            size
     * @param size the size
     * @return String
     * @throws Exception the exception
     */


    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> getDataFromES(final String url, String dataSource, String entityType,
                                                          Map<String, Object> mustFilter, final Map<String, Object> mustNotFilter,
                                                          final HashMultimap<String, Object> shouldFilter, List<String> fields, long from, long size, String uniqueColumn)
            throws Exception {

        // if filter is not null apply filter, this can be a multi value filter
        // also if from and size are -1 -1 send all the data back and do not
        // paginate
        if (Strings.isNullOrEmpty(url)) {
            logger.error("url cannot be null / empty");
            throw new Exception("url parameter cannot be empty or null");
        }

        String urlToQuery = url + "/" + dataSource +
                "/" + "_search";
        logger.info("Querying ES with URL1: {}", urlToQuery);
        String urlToPIT = url + "/" + "_search";
        List<Map<String, String>> results = new ArrayList<Map<String, String>>();
        // paginate for breaking the response into smaller chunks

        // add mapping type in the request body
        Map<String, Object> matchFilters = Maps.newHashMap();
        if (entityType != null) {
            if (mustFilter == null) {
                Map<String, String> typeFilter = new HashMap<String, String>();
                typeFilter.put(PacmanSdkConstants.DOC_TYPE, entityType);
                mustFilter.put("match", typeFilter);
            } else {
                mustFilter.put(PacmanSdkConstants.DOC_TYPE, entityType);
                matchFilters.putAll(mustFilter);
            }
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("size", PacmanSdkConstants.ES_PAGE_SIZE);
        requestBody.put(QUERY, CommonUtils.buildQuery(matchFilters, mustNotFilter, shouldFilter));
        requestBody.put("_source", fields);
        List<Map<String, Object>> sortList = new ArrayList<>();
        Map<String, Object> sortKey = new HashMap<>();
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("order", "desc");
        /**
         *  Add unmapped_type to ignore error in case of no data available in the index
         *         This would also swallow error if the field also not available in the index.
         *         So be very careful that this uniqueColumn exists in the index
         */
        orderMap.put("unmapped_type", "string");
        String sortColumn = uniqueColumn == null ? "_id" : uniqueColumn + ".keyword";
        sortKey.put(sortColumn, orderMap);
        sortList.add(sortKey);
        requestBody.put("sort", sortList);
        Gson serializer = new GsonBuilder().disableHtmlEscaping().create();
        try {
            for (int index = 0; index <= (size / PacmanSdkConstants.ES_PAGE_SIZE); index++) {
                String responseDetails;
                try {
                    String request = serializer.toJson(requestBody);
                    logger.info("Querying ES with URL2: {}", urlToQuery);
                    logger.debug("inventory query" + request);
                    responseDetails = CommonUtils.doHttpPost(urlToQuery, request, new HashMap<>());
                    Map<String, Object> returnObj = processResponseAndSendTheSortObjBack(responseDetails, results);
                    requestBody.put("search_after", returnObj.get("sortArray"));

                } catch (Exception e) {
                    logger.error("error retrieving inventory from ES", e);
                    throw e;
                }
            }
        } finally {
            // TODO: Delete the PIT
        }
        // checkDups(results);
        return results;
    }

    private static String getPitId(String urlToQuery) {
        String response = null;
        response = CommonUtils.doHttpPost(urlToQuery, "", new HashMap<>());
        Gson serializer = new GsonBuilder().create();
        Map<String, Object> responseDetails = (Map<String, Object>) serializer.fromJson(response, Object.class);
        return (String) responseDetails.get("pit_id");
    }

    /**
     * Check dups.
     *
     * @param results the results
     */
    private static void checkDups(List<Map<String, String>> results) {
        Set<String> uniqueIds = results.parallelStream().map(e -> e.get("_docid")).collect(Collectors.toSet());
        if (results.size() != uniqueIds.size()) {
            logger.error("we have a duplicate......" + (results.size() - uniqueIds.size()));
        }
    }

    /**
     * Builds the scroll request.
     *
     * @param _pitId      the scroll id
     * @param requestBody
     * @return the string
     */
    private static String buildPITRequest(String _pitId, Map<String, Object> requestBody) {

        if (_pitId != null) {
            HashMap<String, String> pitBody = new HashMap<>();
            pitBody.put("keep_alive", PacmanSdkConstants.ES_PAGE_SCROLL_TTL);
            pitBody.put("id", _pitId);
            requestBody.put("pit", pitBody);
        }
        Gson serializer = new GsonBuilder().disableHtmlEscaping().create();
        return serializer.toJson(requestBody);
    }

    /**
     * Process response and send the scroll back.
     *
     * @param responseDetails the response details
     * @param results         the results
     * @return the string
     */
    private static Map<String, Object> processResponseAndSendTheSortObjBack(String responseDetails,
                                                                            List<Map<String, String>> results) {
        Gson serializer = new GsonBuilder().create();
        Map<String, Object> response = (Map<String, Object>) serializer.fromJson(responseDetails, Object.class);
        Map<String, Object> lastHitDetail = new HashMap<>();
        if (response.containsKey("hits")) {
            Map<String, Object> hits = (Map<String, Object>) response.get("hits");
            if (hits.containsKey("hits")) {
                List<Map<String, Object>> hitDetails = (List<Map<String, Object>>) hits.get("hits");
                for (Map<String, Object> hitDetail : hitDetails) {
                    Map<String, Object> sources = (Map<String, Object>) hitDetail.get("_source");
                    sources.put(PacmanSdkConstants.ES_DOC_ID_KEY, hitDetail.get(PacmanSdkConstants.ES_DOC_ID_KEY));
//                    sources.put(PacmanSdkConstants.ES_DOC_PARENT_KEY,
//                            hitDetail.get(PacmanSdkConstants.ES_DOC_PARENT_KEY));
                    sources.put(PacmanSdkConstants.ES_DOC_ROUTING_KEY,
                            hitDetail.get(PacmanSdkConstants.ES_DOC_ROUTING_KEY));
                    results.add(CommonUtils.flatNestedMap(null, sources));
                    lastHitDetail = hitDetail;
                }
            }
        }
        // extract the sort array from the last hitDetail object
        List<String> sortArray = (List<String>) lastHitDetail.get("sort");
        Map<String, Object> returnObject = new HashMap<>();
        returnObject.put("sortArray", sortArray);
//        returnObject.put("pit_id", response.get("pit_id"));
        return returnObject;
    }

    /**
     * Convert attributeto keyword.
     *
     * @param attributeName the attribute name
     * @return the string
     */
    public static String convertAttributeToKeyword(String attributeName) {
        return attributeName + ".keyword";
    }

    /**
     * return the ES document for @_id.
     *
     * @param index the index
     * @param targetType the target type
     * @param _id the id
     * @return the document for id
     * @throws Exception the exception
     */
    public static Map<String, String> getDocumentForId(String index, String targetType, String _id) throws Exception {
        String url = ESUtils.getEsUrl();
        Map<String, Object> filter = new HashMap<>();
        filter.put("_id", _id);
        List<String> fields = new ArrayList<String>();
        List<Map<String, String>> details = getDataFromES(url, index.toLowerCase(), targetType.toLowerCase(), filter,
                null, null, fields, 0, 100, "_docid");
        if (details != null && !details.isEmpty()) {
            return details.get(0);
        } else {
            return new HashMap<>();
        }
    }

    /**
     * Publish metrics.
     *
     * @param evalResults the eval results
     * @return the boolean
     */
    public static Boolean publishMetrics(Map<String, Object> evalResults,String type) {
        //logger.info(Joiner.on("#").withKeyValueSeparator("=").join(evalResults));
        String indexName = CommonUtils.getPropValue(PacmanSdkConstants.STATS_INDEX_NAME_KEY);// "fre-stats";
        return doESPublish(evalResults, indexName, type);
    }

    /**
     * Gets the ES port.
     *
     * @return the ES port
     */
    public static int getESPort() {
        return Integer.parseInt(CommonUtils.getPropValue(PacmanSdkConstants.PAC_ES_PORT_KEY));
    }

    /**
     * Gets the ES host.
     *
     * @return the ES host
     */
    public static String getESHost() {
        // TODO Auto-generated method stub
        return CommonUtils.getPropValue(PacmanSdkConstants.PAC_ES_HOST_KEY);
    }

    /**
     * Do ES publish.
     *
     * @param evalResults the eval results
     * @param indexName the index name
     * @param type the type
     * @return the boolean
     */
    public static Boolean doESPublish(Map<String, Object> evalResults, String indexName, String type) {
        
        Gson serializer = new GsonBuilder().create();
        String postBody = serializer.toJson(evalResults);
        return postJsonDocumentToIndexAndType(evalResults.get(PacmanSdkConstants.EXECUTION_ID).toString(),indexName, type,postBody,Boolean.FALSE);
    }
    
    /**
     * Do ES publish.
     *
     * @param evalResults the eval results
     * @param indexName the index name
     * @param type the type
     * @return the boolean
     */
    public static Boolean doESUpdate(String docId,Map<String, Object> evalResults, String indexName, String type) {
        Gson serializer = new GsonBuilder().create();
        String postBody = serializer.toJson(evalResults);
        return postJsonDocumentToIndexAndType(docId,indexName, type,postBody,Boolean.TRUE);
    }

    /**
     * @param executionId
     * @param indexName
     * @param type
     * @param postBody
     * @return
     */
    private static Boolean postJsonDocumentToIndexAndType(String executionId, String indexName, String type,
            String postBody,Boolean isUpdate) {
        String url = ESUtils.getEsUrl();
        if(Strings.isNullOrEmpty(url)){
            logger.error("unable to find ES url");
            return false;
        }
        try {
            if (!ESUtils.isValidIndex(url, indexName)) {
                ESUtils.createIndex(url, indexName);
            }
//            if (!ESUtils.isValidType(url, indexName, type)) {
//                ESUtils.createMapping(url, indexName, type);
//            }
            String esUrl = new StringBuilder(url).append("/").append(indexName).append("/_doc/")
                    .append(executionId).toString();
            if(isUpdate){
                esUrl += "/_update";
            }
            
            CommonUtils.doHttpPost(esUrl,postBody,new HashMap<>());
        } catch (Exception e) {
            logger.error("unable to publish execution stats");
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    /**
     * Creates the keyword.
     *
     * @param key the key
     * @return the string
     */
    public static String createKeyword(final String key) {
        return new StringBuilder(key).append(".").append(PacmanSdkConstants.ES_KEYWORD_KEY).toString();
    }

}
