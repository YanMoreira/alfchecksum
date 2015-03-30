package org.ootb.web.evaluator.doclib.indicator;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.web.evaluator.BaseEvaluator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ChecksumEvaluator extends BaseEvaluator {
	private static final String ASPECT_HASHABLE = "ootb:hashable";
	@Override
	public boolean evaluate(JSONObject jsonObject) {
		try{
			JSONArray nodeAspects = getNodeAspects(jsonObject);
			if(nodeAspects == null){
				return false;
			}
			else{
				if(nodeAspects.contains(ASPECT_HASHABLE)){
					return true;
				}
			}
		}catch(Exception e){
			throw new AlfrescoRuntimeException("Failed to run UI evaluator : " + e.getMessage());
		}
		return false;
	}
}
