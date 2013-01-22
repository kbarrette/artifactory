/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.artifactory.webapp.wicket.page.search.gavc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Mirko Friedenhagen
 */
class GavcQueryToMap {

    private final String groupId;

    private final String artifactId;

    public final String version;

    public final String classifier;

    public GavcQueryToMap(final String query) {
        final String[] split = StringUtils.splitPreserveAllTokens(query, ':');
        if (split.length != 4) {
            this.groupId = "";
            this.artifactId = "";
            this.version = "";
            this.classifier = "";
        } else {
            this.groupId = split[0];
            this.artifactId = split[1];
            this.version = split[2];
            this.classifier = split[3];
        }
    }

    /**
     * @return the groupIdField
     */
    String getGroupId() {
        return groupId;
    }

    /**
     * @return the artifactIdField
     */
    String getArtifactId() {
        return artifactId;
    }

    /**
     * @return the version
     */
    String getVersion() {
        return version;
    }

    /**
     * @return the classifier
     */
    String getClassifier() {
        return classifier;
    }
}
