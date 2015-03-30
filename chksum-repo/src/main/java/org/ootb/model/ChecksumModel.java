package org.ootb.model;

import org.alfresco.service.namespace.QName;

public interface ChecksumModel {
	static final String CHKSUM_MODEL_URI    = "http://orderofthebee.org/model/checksum/1.0";
	static final String CHKSUM_MODEL_PREFIX = "ootb";
	
	static final QName ASPECT_HASHABLE = QName.createQName(CHKSUM_MODEL_URI, "hashable");
	static final QName PROP_HASHTYPE   = QName.createQName(CHKSUM_MODEL_URI, "hashType");
	static final QName PROP_HASHVALUE  = QName.createQName(CHKSUM_MODEL_URI, "hashValue");
}
