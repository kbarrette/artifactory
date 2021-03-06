/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2012 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.artifactory.storage.db.itest;

import ch.qos.logback.classic.util.ContextInitializer;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.spring.SpringConfigPaths;
import org.artifactory.storage.StorageProperties;
import org.artifactory.storage.db.DbServiceImpl;
import org.artifactory.storage.db.util.DbUtils;
import org.artifactory.storage.db.util.JdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.TestUtils;
import org.artifactory.util.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Base class for the low level database integration tests.
 *
 * @author Yossi Shaul
 */
//@TestExecutionListeners(TransactionalTestExecutionListener.class)
//@Transactional
//@TransactionConfiguration(defaultRollback = false)
@Test(groups = "dbtest")
@ContextConfiguration(locations = {"classpath:spring/db-test-context.xml"})
public abstract class DbBaseTest extends AbstractTestNGSpringContextTests {

    @Autowired
    protected JdbcHelper jdbcHelper;

    @Autowired
    protected DbServiceImpl dbService;

    @Autowired
    @Qualifier("storageProperties")
    private StorageProperties storageProperties;

    private ArtifactoryHomeBoundTest artifactoryHomeBoundTest;

    static {
        // use the itest logback config
        URL url = DbBaseTest.class.getClassLoader().getResource("logback-dbtest.xml");
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, url.getPath());
    }

    @BeforeClass
    @Override
    protected void springTestContextPrepareTestInstance() throws Exception {
        artifactoryHomeBoundTest = createArtifactoryHomeTest();
        artifactoryHomeBoundTest.bindArtifactoryHome();

        super.springTestContextPrepareTestInstance();

        try (Connection connection = jdbcHelper.getDataSource().getConnection()) {
            DbTestUtils.refreshOrRecreateSchema(connection, storageProperties.getDbType());
        }
        TestUtils.invokeMethodNoArgs(dbService, "initializeIdGenerator");
    }

    protected ArtifactoryHomeBoundTest createArtifactoryHomeTest() throws IOException {
        return new ArtifactoryHomeBoundTest();
    }

    protected String randomMd5() {
        return randomHexa(32);
    }

    protected String randomSha1() {
        return randomHexa(40);
    }

    private String randomHexa(int count) {
        return RandomStringUtils.random(count, "abcdef0123456789".toCharArray());
    }

    public void verifyDbResourcesReleased() throws IOException, SQLException {
        // make sure there are no active connections
        PoolingDataSource ds = (PoolingDataSource) jdbcHelper.getDataSource();
        GenericObjectPool pool = TestUtils.getField(ds, "_pool", GenericObjectPool.class);
        assertEquals(pool.getNumActive(), 0, "Found " + pool.getNumActive() + " active connections after test ended:\n"
                + TestUtils.invokeMethodNoArgs(pool, "debugInfo"));
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    @BeforeMethod
    public void bindArtifactoryHome() {
        artifactoryHomeBoundTest.bindArtifactoryHome();
    }

    @AfterMethod
    public void unbindArtifactoryHome() {
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    protected void importSql(String resourcePath) {
        InputStream resource = ResourceUtils.getResource(resourcePath);
        Connection con = null;
        try {
            con = jdbcHelper.getDataSource().getConnection();
            DbUtils.executeSqlStream(con, resource);
            // update the id generator
            TestUtils.invokeMethodNoArgs(dbService, "initializeIdGenerator");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            DbUtils.close(con);
        }
    }

    @BeforeMethod
    public void bindDummyContext() {
        ArtifactoryContextThreadBinder.bind(new DummyArtifactoryContext());
    }

    @AfterMethod
    public void unbindDummyContext() {
        ArtifactoryContextThreadBinder.unbind();
    }

    public class DummyArtifactoryContext implements ArtifactoryContext {
        @Override
        public CentralConfigService getCentralConfig() {
            return applicationContext.getBean(CentralConfigService.class);
        }

        @Override
        public <T> T beanForType(Class<T> type) {
            return applicationContext.getBean(type);
        }

        @Override
        public <T> T beanForType(String name, Class<T> type) {
            return applicationContext.getBean(name, type);
        }

        @Override
        public <T> Map<String, T> beansForType(Class<T> type) {
            return applicationContext.getBeansOfType(type);
        }

        @Override
        public Object getBean(String name) {
            return applicationContext.getBean(name);
        }

        @Override
        public RepositoryService getRepositoryService() {
            return applicationContext.getBean(RepositoryService.class);
        }

        @Override
        public AuthorizationService getAuthorizationService() {
            return applicationContext.getBean(AuthorizationService.class);
        }

        @Override
        public long getUptime() {
            return 0;
        }

        @Override
        public ArtifactoryHome getArtifactoryHome() {
            return ArtifactoryHome.get();
        }

        @Override
        public String getContextId() {
            return null;
        }

        @Override
        public SpringConfigPaths getConfigPaths() {
            return null;
        }

        @Override
        public void exportTo(ExportSettings settings) {
            throw new UnsupportedOperationException("No export here");
        }

        @Override
        public void importFrom(ImportSettings settings) {
            throw new UnsupportedOperationException("No import here");
        }
    }

}
