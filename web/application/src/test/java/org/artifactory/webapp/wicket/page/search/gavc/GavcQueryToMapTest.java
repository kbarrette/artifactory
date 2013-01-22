/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.artifactory.webapp.wicket.page.search.gavc;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 *
 * @author Mirko Friedenhagen <mirko.friedenhagen@1und1.de>
 */
public class GavcQueryToMapTest {

    @Test
    public void testArtifactIdQuery() {
        GavcQueryToMap sut = new GavcQueryToMap(":maven-release-plugin::");
        assertEquals(sut.getGroupId(), "");
        assertEquals(sut.getArtifactId(), "maven-release-plugin");
        assertEquals(sut.getVersion(), "");
        assertEquals(sut.getClassifier(), "");
    }

    @Test
    public void testGroupIdAndArtifactIdQuery() {
        GavcQueryToMap sut = new GavcQueryToMap("org.apache.maven.plugins:maven-release-plugin::");
        assertEquals(sut.getGroupId(), "org.apache.maven.plugins");
        assertEquals(sut.getArtifactId(), "maven-release-plugin");
        assertEquals(sut.getVersion(), "");
        assertEquals(sut.getClassifier(), "");
    }

    @Test
    public void testGroupIdAndArtifactIdAndVersionQuery() {
        GavcQueryToMap sut = new GavcQueryToMap("org.apache.maven.plugins:maven-release-plugin:1.4*:");
        assertEquals(sut.getGroupId(), "org.apache.maven.plugins");
        assertEquals(sut.getArtifactId(), "maven-release-plugin");
        assertEquals(sut.getVersion(), "1.4*");
        assertEquals(sut.getClassifier(), "");
    }
    @Test
    public void testGroupIdAndArtifactIdAndVersionAndClassifierQuery() {
        GavcQueryToMap sut = new GavcQueryToMap("org.apache.maven.plugins:maven-release-plugin:1.4*:sources");
        assertEquals(sut.getGroupId(), "org.apache.maven.plugins");
        assertEquals(sut.getArtifactId(), "maven-release-plugin");
        assertEquals(sut.getVersion(), "1.4*");
        assertEquals(sut.getClassifier(), "sources");
    }
}
