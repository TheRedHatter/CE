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
/**
  Copyright (C) 2017 T Mobile Inc - All Rights Reserve
  Purpose:
  Author :u55262
  Modified Date: Sep 19, 2017
  
 **/
package com.tmobile.cloud.awsrules.ec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import com.tmobile.cloud.awsrules.utils.PacmanUtils;
import com.tmobile.cloud.constants.PacmanRuleConstants;
import com.tmobile.pacman.commons.PacmanSdkConstants;
import com.tmobile.pacman.commons.exception.InvalidInputException;
import com.tmobile.pacman.commons.exception.RuleExecutionFailedExeption;
import com.tmobile.pacman.commons.policy.Annotation;
import com.tmobile.pacman.commons.policy.BasePolicy;
import com.tmobile.pacman.commons.policy.PacmanPolicy;
import com.tmobile.pacman.commons.policy.PolicyResult;

/**
 * The Class ResourceScannedByQualysRule.
 */
@PacmanPolicy(key = "check-for-resource-scanned-by-qualys", desc = "checks for Ec2 instance or VM scanned by qualys,if not found then its an issue", severity = PacmanSdkConstants.SEV_HIGH, category = PacmanSdkConstants.GOVERNANCE)
public class ResourceScannedByQualysRule extends BasePolicy {

	/** The Constant logger. */
	private static final Logger logger = LoggerFactory.getLogger(ResourceScannedByQualysRule.class);
	
	/**
	 * The method will get triggered from Rule Engine with following parameters.
	 *
	 * @param ruleParam ************* Following are the Rule Parameters********* <br><br>
	 * 
	 * ruleKey : check-for-resource-scanned-by-qualys <br><br>
	 * 
	 * target : Enter the target days <br><br>
	 * 
	 * discoveredDaysRange : Enter the discovered days Range <br><br>
	 * 
	 * esQualysUrl : Enter the qualys URL <br><br>
	 * 
	 * @param resourceAttributes this is a resource in context which needs to be scanned this is provided by execution engine
	 * @return the rule result
	 */

	public PolicyResult execute(final Map<String, String> ruleParam,Map<String, String> resourceAttributes) {
		logger.debug("========ResourceScannedByQualysRule started=========");
		Annotation annotation = null;
		String instanceId = null;
		String severity = ruleParam.get(PacmanRuleConstants.SEVERITY);
		String category = ruleParam.get(PacmanRuleConstants.CATEGORY);
		String target = ruleParam.get(PacmanRuleConstants.TARGET);
		String firstDiscoveredOn = resourceAttributes.get(PacmanRuleConstants.FIRST_DISCOVERED_ON);
		String discoveredDaysRange = ruleParam.get(PacmanRuleConstants.DISCOVERED_DAYS_RANGE);
		if(!StringUtils.isNullOrEmpty(firstDiscoveredOn)){
		firstDiscoveredOn= firstDiscoveredOn.substring(0,PacmanRuleConstants.FIRST_DISCOVERED_DATE_FORMAT_LENGTH);
		}
		String qualysApi =  null;
		
		String formattedUrl = PacmanUtils.formatUrl(ruleParam,PacmanRuleConstants.ES_QUALYS_URL);
        
        if(!StringUtils.isNullOrEmpty(formattedUrl)){
            qualysApi =  formattedUrl;
        }
        
		MDC.put("executionId", ruleParam.get("executionId")); // this is the logback Mapped Diagnostic Contex
		MDC.put("ruleId", ruleParam.get(PacmanSdkConstants.POLICY_ID)); // this is the logback Mapped Diagnostic Contex		
		
		if (!PacmanUtils.doesAllHaveValue(severity,category,qualysApi,target,discoveredDaysRange)) {
			logger.info(PacmanRuleConstants.MISSING_CONFIGURATION);
			throw new InvalidInputException(PacmanRuleConstants.MISSING_CONFIGURATION);
		}

		List<LinkedHashMap<String,Object>> issueList = new ArrayList<>();
		LinkedHashMap<String,Object> issue = new LinkedHashMap<>();
		Gson gson = new Gson();
		
		if (resourceAttributes != null && (PacmanRuleConstants.RUNNING_STATE.equalsIgnoreCase(resourceAttributes.get(PacmanRuleConstants.STATE_NAME)) || PacmanRuleConstants.RUNNING_STATE.equalsIgnoreCase(resourceAttributes.get(PacmanRuleConstants.STATUS)))) {
			String entityType = resourceAttributes.get(PacmanRuleConstants.ENTITY_TYPE);
			instanceId = StringUtils.trim(resourceAttributes.get(PacmanRuleConstants.RESOURCE_ID));
			if(PacmanUtils.calculateLaunchedDuration(firstDiscoveredOn)>Long.parseLong(discoveredDaysRange)){
			    Map<String,Object> ec2ScannesByQualysMap = new HashMap<>();
			try{
				ec2ScannesByQualysMap = PacmanUtils.checkInstanceIdFromElasticSearchForQualys(instanceId,qualysApi,"_resourceid",target);
			} catch (Exception e) {
				logger.error("unable to determine",e);
				throw new RuleExecutionFailedExeption("unable to determine"+e);
			}
			if (!ec2ScannesByQualysMap.isEmpty()) {
				annotation = Annotation.buildAnnotation(ruleParam,Annotation.Type.ISSUE);
				annotation.put(PacmanSdkConstants.DESCRIPTION,""+entityType+" instance not scanned  by qualys found!!");
				annotation.put(PacmanRuleConstants.SEVERITY, severity);
				annotation.put(PacmanRuleConstants.CATEGORY, category);
				
				issue.put(PacmanRuleConstants.VIOLATION_REASON, ""+entityType+" instance not scanned by qualys found");
				issue.put(PacmanRuleConstants.SOURCE_VERIFIED, "_resourceid,"+PacmanRuleConstants.LAST_VULN_SCAN);
				issue.put(PacmanRuleConstants.FAILED_REASON_QUALYS, gson.toJson(ec2ScannesByQualysMap));
				issueList.add(issue);
				annotation.put("issueDetails", issueList.toString());
				
				logger.debug("========ResourceScannedByQualysRule ended with annotation {} : =========" ,annotation);
				return new PolicyResult(PacmanSdkConstants.STATUS_FAILURE,PacmanRuleConstants.FAILURE_MESSAGE, annotation);
			}
		}
		}
		
		logger.debug("========ResourceScannedByQualysRule ended=========");
		return new PolicyResult(PacmanSdkConstants.STATUS_SUCCESS,PacmanRuleConstants.SUCCESS_MESSAGE);
	}

	/* (non-Javadoc)
	 * @see com.tmobile.pacman.commons.rule.Rule#getHelpText()
	 */
	public String getHelpText() {
		return "This rule checks for Ec2 instance or VM scanned by qualys,if not found then its an issue";
	}

}
