package org.ootb.repo;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentServicePolicies.OnContentUpdatePolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnAddAspectPolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnUpdatePropertiesPolicy;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ootb.model.ChecksumModel;

public class ContentHasher implements OnAddAspectPolicy, OnContentUpdatePolicy,
		OnUpdatePropertiesPolicy {
	Log logger = LogFactory.getLog(ContentHasher.class);

	private static final String DEFAULT_HASH_TYPE = "md5";
	private static final int BUFFER_SIZE = 1 << 8;

	private Behaviour onAddAspect;
	private Behaviour onContentUpdate;
	private Behaviour onUpdateProperties;

	private PolicyComponent policyComponent;
	private NodeService nodeService;
	private BehaviourFilter policyFilter;
	private ContentService contentService;

	public void setPolicyComponent(PolicyComponent policyComponent) {
		this.policyComponent = policyComponent;
	}

	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	public void setPolicyFilter(BehaviourFilter policyFilter) {
		this.policyFilter = policyFilter;
	}

	public void setContentService(ContentService contentService) {
		this.contentService = contentService;
	}

	public void init() {
		onAddAspect = new JavaBehaviour(this, "onAddAspect",
				Behaviour.NotificationFrequency.TRANSACTION_COMMIT);
		policyComponent
				.bindClassBehaviour(QName.createQName(
						NamespaceService.ALFRESCO_URI, "onAddAspect"),
						ChecksumModel.ASPECT_HASHABLE, onAddAspect);

		onContentUpdate = new JavaBehaviour(this, "onContentUpdate",
				Behaviour.NotificationFrequency.TRANSACTION_COMMIT);
		policyComponent.bindClassBehaviour(QName.createQName(
				NamespaceService.ALFRESCO_URI, "onContentUpdate"),
				ChecksumModel.ASPECT_HASHABLE, onContentUpdate);

		onUpdateProperties = new JavaBehaviour(this, "onUpdateProperties",
				Behaviour.NotificationFrequency.TRANSACTION_COMMIT);

		policyComponent.bindClassBehaviour(QName.createQName(
				NamespaceService.ALFRESCO_URI, "onUpdateProperties"),
				ChecksumModel.ASPECT_HASHABLE, onUpdateProperties);

	}

	@Override
	public void onAddAspect(NodeRef nodeRef, QName aspectTypeQName) {
		if (!nodeService.exists(nodeRef)
				|| !nodeService.hasAspect(nodeRef,ChecksumModel.ASPECT_HASHABLE)
				|| nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY)
				|| (nodeService.getProperty(nodeRef,ChecksumModel.PROP_HASHVALUE) != null)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot process nodeRef.");
				return;
			}
		}
		setHash(nodeRef, DEFAULT_HASH_TYPE);
	}

	@Override
	public void onContentUpdate(NodeRef nodeRef, boolean newContent) {
		if (!nodeService.exists(nodeRef)
				|| !nodeService.hasAspect(nodeRef,ChecksumModel.ASPECT_HASHABLE)
				|| nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY)
				|| (nodeService.getProperty(nodeRef,ChecksumModel.PROP_HASHVALUE) != null)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot process nodeRef.");
				return;
			}
		}
		String digestType = (String) nodeService.getProperty(nodeRef, ChecksumModel.PROP_HASHTYPE);
		setHash(nodeRef, digestType);
	}

	@Override
	public void onUpdateProperties(NodeRef nodeRef, Map<QName, Serializable> before,
			Map<QName, Serializable> after) {
		if (!nodeService.exists(nodeRef)
				|| !nodeService.hasAspect(nodeRef,ChecksumModel.ASPECT_HASHABLE)
				|| nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY)
				|| (nodeService.getProperty(nodeRef,ChecksumModel.PROP_HASHVALUE) != null)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot process nodeRef.");
				return;
			}
		}
		String oldHashType = (String) before.get(ChecksumModel.PROP_HASHTYPE);
		String newHashType = (String) after.get(ChecksumModel.PROP_HASHTYPE);
		if (oldHashType.equals(newHashType)) {
			if (logger.isDebugEnabled())
				logger.debug("No change in hash type.");
		}
		setHash(nodeRef, newHashType);
	}

	private void setHash(NodeRef nodeRef, String hashType) {
		policyFilter.disableBehaviour(ContentModel.ASPECT_VERSIONABLE);
		ContentReader contentReader = contentService.getReader(nodeRef,
				ContentModel.PROP_CONTENT);
		if (contentReader == null || contentReader.getSize() == 0) {
			logger.error("Content is null or empty, removing aspect.");
			removeAspect(nodeRef);
			return;
		}
		InputStream contentStream = contentReader.getContentInputStream();
		String hashValue = computeHash(contentStream, hashType);
		if(hashValue == null){
			removeAspect(nodeRef);
			return;
		}
		nodeService.setProperty(nodeRef, ChecksumModel.PROP_HASHTYPE, hashType);
		nodeService.setProperty(nodeRef, ChecksumModel.PROP_HASHVALUE, hashValue);
		policyFilter.enableBehaviour(ContentModel.ASPECT_VERSIONABLE);
	}

	private void removeAspect(NodeRef nodeRef) {
		nodeService.removeAspect(nodeRef, ChecksumModel.ASPECT_HASHABLE);
	}

	private String computeHash(InputStream contentStream, String hashType) {
		MessageDigest messageDigest = null;
		try{
			messageDigest = MessageDigest.getInstance(hashType);
		}catch(NoSuchAlgorithmException e){
			logger.error("Unable to process algorithm for hashtype : " + hashType);
			return null;
		}
		
		messageDigest.reset();
		byte[] buffer = new byte[BUFFER_SIZE];
		int bytesRead = -1;
		try {
			while ((bytesRead = contentStream.read(buffer)) > -1) {
				messageDigest.update(buffer, 0, bytesRead);
			}
		} catch (IOException e) {
			logger.error("Unable to read content stream.", e);
			return null;
		} finally {
			try {
				contentStream.close();
			} catch (IOException e) {}
		}
		byte[] digest = messageDigest.digest();
		return convertByteArrayToHex(digest);
	}

	private String convertByteArrayToHex(byte[] array) {
		StringBuffer hashValue = new StringBuffer();
		for (int i = 0; i < array.length; i++) {
			String hex = Integer.toHexString(0xFF & array[i]);
			if (hex.length() == 1) {
				hashValue.append('0');
			}
			hashValue.append(hex);
		}
		return hashValue.toString().toUpperCase(); 
	}
}
