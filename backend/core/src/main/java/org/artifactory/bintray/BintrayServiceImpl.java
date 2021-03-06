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

package org.artifactory.bintray;

import com.google.common.collect.MapMaker;
import com.google.common.collect.Sets;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.bintray.BintrayItemInfo;
import org.artifactory.api.bintray.BintrayPackageInfo;
import org.artifactory.api.bintray.BintrayParams;
import org.artifactory.api.bintray.BintrayService;
import org.artifactory.api.bintray.BintrayUser;
import org.artifactory.api.bintray.Repo;
import org.artifactory.api.bintray.RepoPackage;
import org.artifactory.api.bintray.exception.BintrayException;
import org.artifactory.api.common.MultiStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.mail.MailService;
import org.artifactory.api.search.BintrayItemSearchResults;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.build.InternalBuildService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.StatusEntry;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.md.PropertiesFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.search.VfsQueryResult;
import org.artifactory.sapi.search.VfsQueryRow;
import org.artifactory.sapi.search.VfsQueryService;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.security.UserInfo;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.binstore.service.BinaryStore;
import org.artifactory.util.EmailException;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.PathUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.type.TypeReference;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @author Shay Yaakov
 */
@Service
public class BintrayServiceImpl implements BintrayService {
    private static final Logger log = LoggerFactory.getLogger(BintrayServiceImpl.class);
    private static final String RANGE_LIMIT_TOTAL = "X-RangeLimit-Total";
    @Autowired
    private UserGroupService userGroupService;
    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private InternalRepositoryService repoService;
    @Autowired
    private BinaryStore binaryStore;
    @Autowired
    private InternalBuildService buildService;
    @Autowired
    private MailService mailService;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private CachedThreadPoolTaskExecutor executor;
    @Autowired
    private VfsQueryService vfsQueryService;
    @Autowired
    protected CentralConfigService centralConfig;
    /**
     * Bintray Rest API request Cache
     */
    private Map<String, BintrayItemInfo> bintrayItemInfoCache;
    private Map<String, BintrayPackageInfo> bintrayPackageCache;

    public BintrayServiceImpl() {
        bintrayItemInfoCache = initCache(500, TimeUnit.HOURS.toSeconds(1), false);
        bintrayPackageCache = initCache(500, TimeUnit.HOURS.toSeconds(1), false);
    }

    @Override
    public MultiStatusHolder pushArtifact(ItemInfo itemInfo, BintrayParams bintrayParams) throws IOException {
        MultiStatusHolder status = new MultiStatusHolder();

        HttpClient client = createHTTPClient();
        if (itemInfo.isFolder()) {
            List<ItemInfo> children = repoService.getChildrenDeeply(itemInfo.getRepoPath());
            for (ItemInfo child : children) {
                if (!child.isFolder()) {
                    performPush(client, (FileInfo) itemInfo, bintrayParams, status);
                }
            }
        } else {
            performPush(client, (FileInfo) itemInfo, bintrayParams, status);
        }

        return status;
    }

    @Override
    public MultiStatusHolder pushBuild(Build build, BintrayParams bintrayParams) throws IOException {
        MultiStatusHolder status = new MultiStatusHolder();
        String buildNameAndNumber = build.getName() + ":" + build.getNumber();
        status.setStatus("Starting pushing build '" + buildNameAndNumber + "' to Bintray.", log);
        Set<FileInfo> artifactsToPush = collectArtifactsToPush(build, status);
        if (artifactsToPush != null) {
            HttpClient client = createHTTPClient();
            status.setStatus("Found " + artifactsToPush.size() + " artifacts to push.", log);
            for (FileInfo fileInfo : artifactsToPush) {
                bintrayParams.setPath(fileInfo.getRelPath());
                if (bintrayParams.isUseExistingProps()) {
                    BintrayParams paramsFromProperties = createParamsFromProperties(fileInfo.getRepoPath());
                    bintrayParams.setRepo(paramsFromProperties.getRepo());
                    bintrayParams.setPackageId(paramsFromProperties.getPackageId());
                    bintrayParams.setVersion(paramsFromProperties.getVersion());
                    bintrayParams.setPath(paramsFromProperties.getPath());
                }
                try {
                    performPush(client, fileInfo, bintrayParams, status);
                } catch (IOException e) {
                    sendBuildPushNotification(status, buildNameAndNumber);
                    throw e;
                }
            }
        }

        String message = String.format("Finished pushing build '%s' to Bintray with %s errors and %s warnings.",
                buildNameAndNumber, status.getErrors().size(), status.getWarnings().size());
        status.setStatus(message, log);

        if (bintrayParams.isNotify()) {
            sendBuildPushNotification(status, buildNameAndNumber);
        }

        return status;
    }

    private <V> Map<String, V> initCache(int initialCapacity, long expirationSeconds, boolean softValues) {
        MapMaker mapMaker = new MapMaker().initialCapacity(initialCapacity);
        if (expirationSeconds >= 0) {
            mapMaker.expireAfterWrite(expirationSeconds, TimeUnit.SECONDS);
        }
        if (softValues) {
            mapMaker.softValues();
        }

        return mapMaker.makeMap();
    }

    @Override
    public void executeAsyncPushBuild(Build build, BintrayParams bintrayParams) {
        try {
            pushBuild(build, bintrayParams);
        } catch (IOException e) {
            log.error("Push failed with exception: " + e.getMessage());
        }
    }

    private void sendBuildPushNotification(MultiStatusHolder statusHolder, String buildNameAndNumber)
            throws IOException {
        log.info("Sending logs for push build '{}' by mail.", buildNameAndNumber);
        InputStream stream = null;
        try {
            //Get message body from properties and substitute variables
            stream = getClass().getResourceAsStream("/org/artifactory/email/messages/bintrayPushBuild.properties");
            ResourceBundle resourceBundle = new PropertyResourceBundle(stream);
            String body = resourceBundle.getString("body");
            String logBlock = getLogBlock(statusHolder);
            UserInfo currentUser = getCurrentUser();
            if (currentUser != null) {
                String userEmail = currentUser.getEmail();
                if (StringUtils.isBlank(userEmail)) {
                    log.warn("Couldn't find valid email address. Skipping push build to bintray email notification");
                } else {
                    log.debug("Sending push build to Bintray notification to '{}'.", userEmail);
                    String message = MessageFormat.format(body, logBlock);
                    mailService.sendMail(new String[]{userEmail}, "Push Build to Bintray Report", message);
                }
            }
        } catch (EmailException e) {
            log.error("Error while notification of: '" + buildNameAndNumber + "' messages.", e);
            throw e;
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private UserInfo getCurrentUser() {
        // currentUser() is not enough since the user might have changed his details from the profile page so the
        // database has the real details while currentUser() is the authenticated user which was not updated.
        try {
            String username = userGroupService.currentUser().getUsername();
            UserInfo userInfo = userGroupService.findUser(username);
            return userInfo;
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns an HTML list block of messages extracted from the status holder
     *
     * @param statusHolder Status holder containing messages that should be included in the notification
     * @return HTML list block
     */
    private String getLogBlock(MultiStatusHolder statusHolder) {
        StringBuilder builder = new StringBuilder();

        for (StatusEntry entry : statusHolder.getAllEntries()) {

            //Make one line per row
            String message = entry.getMessage();
            Throwable throwable = entry.getException();
            if (throwable != null) {
                String throwableMessage = throwable.getMessage();
                if (StringUtils.isNotBlank(throwableMessage)) {
                    message += ": " + throwableMessage;
                }
            }
            builder.append(message).append("<br>");
        }

        builder.append("<p>");

        return builder.toString();
    }

    @Override
    public BintrayParams createParamsFromProperties(RepoPath repoPath) {
        BintrayParams bintrayParams = new BintrayParams();
        Properties properties = repoService.getProperties(repoPath);
        if (properties != null) {
            bintrayParams.setRepo(properties.getFirst(BINTRAY_REPO));
            bintrayParams.setPackageId(properties.getFirst(BINTRAY_PACKAGE));
            bintrayParams.setVersion(properties.getFirst(BINTRAY_VERSION));
            bintrayParams.setPath(properties.getFirst(BINTRAY_PATH));
        }

        return bintrayParams;
    }

    @Override
    public void savePropertiesOnRepoPath(RepoPath repoPath, BintrayParams bintrayParams) {
        Properties properties = repoService.getProperties(repoPath);
        if (properties == null) {
            properties = PropertiesFactory.create();
        }
        properties.replaceValues(BINTRAY_REPO, newArrayList(bintrayParams.getRepo()));
        properties.replaceValues(BINTRAY_PACKAGE, newArrayList(bintrayParams.getPackageId()));
        properties.replaceValues(BINTRAY_VERSION, newArrayList(bintrayParams.getVersion()));
        properties.replaceValues(BINTRAY_PATH, newArrayList(bintrayParams.getPath()));
        repoService.setProperties(repoPath, properties);
    }

    private Set<FileInfo> collectArtifactsToPush(Build build, MultiStatusHolder status) {
        Set<FileInfo> fileInfoSet = Sets.newHashSet();

        List<Module> modules = build.getModules();
        if (modules != null) {
            for (Module module : modules) {
                List<Artifact> artifacts = module.getArtifacts();
                if (artifacts != null) {
                    for (Artifact artifact : artifacts) {
                        Set<FileInfo> artifactInfos = buildService.getBuildFileBeanInfo(build.getName(),
                                build.getNumber(), artifact, false);
                        if (artifactInfos != null && !artifactInfos.isEmpty()) {
                            fileInfoSet.add(artifactInfos.iterator().next());
                        } else {
                            status.setError("Couldn't find matching file for artifact '" + artifact + "'", log);
                            return null;
                        }
                    }
                }
            }
        }

        return fileInfoSet;
    }

    private void performPush(HttpClient client, FileInfo fileInfo, BintrayParams bintrayParams,
            MultiStatusHolder status) throws IOException {
        if (!bintrayParams.isValid()) {
            String message = String.format("Skipping push for '%s' since one of the Bintray properties is missing.",
                    fileInfo.getRelPath());
            status.setWarning(message, log);
            return;
        }

        if (!authorizationService.canAnnotate(fileInfo.getRepoPath())) {
            String message = String.format("Skipping push for '%s' since user doesn't have annotate permissions on it.",
                    fileInfo.getRelPath());
            status.setWarning(message, log);
            return;
        }

        String path = bintrayParams.getPath();
        status.setStatus("Pushing artifact " + path + " to Bintray.", log);
        String requestUrl = getBaseBintrayApiUrl() + PATH_CONTENT + "/" + bintrayParams.getRepo() + "/"
                + bintrayParams.getPackageId() + "/" + bintrayParams.getVersion() + "/" + path;

        PutMethod putMethod = createPutMethod(fileInfo, requestUrl);
        try {
            int statusCode = client.executeMethod(putMethod);
            String message;
            if (statusCode != HttpStatus.SC_CREATED) {
                message = String.format("Push failed for '%s' with status: %s %s", path, statusCode,
                        putMethod.getStatusText());
                status.setError(message, statusCode, log);
            } else {
                message = String.format(
                        "Successfully pushed '%s' to repo: '%s', package: '%s', version: '%s' in Bintray.",
                        path, bintrayParams.getRepo(), bintrayParams.getPackageId(), bintrayParams.getVersion());
                status.setStatus(message, log);
                if (!bintrayParams.isUseExistingProps()) {
                    savePropertiesOnRepoPath(fileInfo.getRepoPath(), bintrayParams);
                }
            }
        } finally {
            putMethod.releaseConnection();
        }
    }

    @Override
    public List<Repo> getReposToDeploy() throws IOException, BintrayException {
        UsernamePasswordCredentials creds = getCurrentUserBintrayCreds();
        String requestUrl = getBaseBintrayApiUrl() + PATH_REPOS + "/" + creds.getUserName();
        InputStream responseStream = null;
        try {
            responseStream = executeGet(requestUrl, creds);
            if (responseStream != null) {
                return JacksonReader.streamAsValueTypeReference(responseStream, new TypeReference<List<Repo>>() {
                });
            }
        } finally {
            IOUtils.closeQuietly(responseStream);
        }
        return null;
    }

    @Override
    public List<String> getPackagesToDeploy(String repoKey) throws IOException, BintrayException {
        UsernamePasswordCredentials creds = getCurrentUserBintrayCreds();
        String requestUrl = getBaseBintrayApiUrl() + PATH_REPOS + "/" + repoKey + "/packages";
        InputStream responseStream = null;
        try {
            responseStream = executeGet(requestUrl, creds);
            if (responseStream != null) {
                return getPackagesList(responseStream);
            }
        } finally {
            IOUtils.closeQuietly(responseStream);
        }
        return null;
    }

    private List<String> getPackagesList(InputStream responseStream) throws IOException {
        List<String> packages = newArrayList();
        JsonNode packagesTree = JacksonReader.streamAsTree(responseStream);
        Iterator<JsonNode> elements = packagesTree.getElements();
        while (elements.hasNext()) {
            JsonNode packageElement = elements.next();
            String packageName = packageElement.get("name").asText();
            boolean linked = packageElement.get("linked").asBoolean();
            if (!linked) {
                packages.add(packageName);
            }
        }
        return packages;
    }

    @Override
    public List<String> getVersions(String repoKey, String packageId) throws IOException, BintrayException {
        UsernamePasswordCredentials creds = getCurrentUserBintrayCreds();
        String requestUrl = getBaseBintrayApiUrl() + PATH_PACKAGES + "/" + repoKey + "/" + packageId;
        InputStream responseStream = null;
        try {
            responseStream = executeGet(requestUrl, creds);
            if (responseStream != null) {
                RepoPackage repoPackage = JacksonReader.streamAsClass(responseStream, RepoPackage.class);
                return repoPackage.getVersions();
            }
        } finally {
            IOUtils.closeQuietly(responseStream);
        }
        return null;
    }

    @Override
    public String getVersionFilesUrl(BintrayParams bintrayParams) {
        return getBaseBintrayUrl() + VERSION_SHOW_FILES + "/" + bintrayParams.getRepo() + "/"
                + bintrayParams.getPackageId() + "/" + bintrayParams.getVersion();
    }

    @Override
    public BintrayUser getBintrayUser(String username, String apiKey) throws IOException, BintrayException {
        String requestUrl = getBaseBintrayApiUrl() + PATH_USERS + "/" + username;
        InputStream responseStream = null;
        try {
            responseStream = executeGet(requestUrl, new UsernamePasswordCredentials(username, apiKey));
            if (responseStream != null) {
                return JacksonReader.streamAsValueTypeReference(responseStream, new TypeReference<BintrayUser>() {
                });
            }
        } finally {
            IOUtils.closeQuietly(responseStream);
        }
        return null;
    }

    @Override
    public boolean isUserHasBintrayAuth() {
        UserInfo userInfo = getCurrentUser();
        if (userInfo != null) {
            String bintrayAuth = userInfo.getBintrayAuth();
            if (StringUtils.isNotBlank(bintrayAuth)) {
                String[] bintrayAuthTokens = StringUtils.split(bintrayAuth, ":");
                if (bintrayAuthTokens.length == 2) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getBintrayRegistrationUrl() {
        String licenseKeyHash = addonsManager.getLicenseKeyHash();
        StringBuilder builder = new StringBuilder(ConstantValues.bintrayUrl.getString()).append("?source=artifactory");
        if (StringUtils.isNotBlank(licenseKeyHash)) {
            builder.append(":").append(licenseKeyHash);
        }
        return builder.toString();
    }

    @Override
    public BintrayItemSearchResults<BintrayItemInfo> searchByName(String query)
            throws IOException, BintrayException {
        String requestUrl = getBaseBintrayApiUrl() + "search/file/?subject=bintray&repo=jcenter&name=" + query;
        GetMethod getMethod = new GetMethod(HttpUtils.encodeQuery(requestUrl));
        HttpClient client = createHTTPClient(new UsernamePasswordCredentials("", ""));
        int status = client.executeMethod(getMethod);
        if (status != HttpStatus.SC_OK) {
            throw new BintrayException(getMethod.getStatusText(), getMethod.getStatusCode());
        } else {
            InputStream responseStream = null;
            try {
                int rangeLimitTotal = Integer.parseInt(getMethod.getResponseHeader(RANGE_LIMIT_TOTAL).getValue());
                responseStream = getMethod.getResponseBodyAsStream();
                List<BintrayItemInfo> listResult = JacksonReader.streamAsValueTypeReference(
                        responseStream, new TypeReference<List<BintrayItemInfo>>() {
                });
                BintrayItemSearchResults<BintrayItemInfo> results = new BintrayItemSearchResults<>(listResult,
                        rangeLimitTotal);
                fillLocalRepoPaths(listResult);
                fixDateFormat(listResult);
                return results;
            } finally {
                IOUtils.closeQuietly(responseStream);
            }
        }
    }

    private void fixDateFormat(List<BintrayItemInfo> listResult) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(Build.STARTED_FORMAT);
        for (BintrayItemInfo bintrayItemInfo : listResult) {
            String createdDateFromBintray = bintrayItemInfo.getCreated();
            long createdDate = ISODateTimeFormat.dateTime().parseMillis(createdDateFromBintray);
            bintrayItemInfo.setCreated(simpleDateFormat.format(new Date(createdDate)));
        }
    }

    private void fillLocalRepoPaths(List<BintrayItemInfo> listResult) {
        for (BintrayItemInfo row : listResult) {
            RepoPath repo = getRepoPath(row);
            row.setCached(repo != null);
            row.setLocalRepoPath(repo);
        }
    }

    private RepoPath getRepoPath(BintrayItemInfo row) {
        RemoteRepoDescriptor jCenterRepo = getJCenterRepo();
        VfsQueryResult result = vfsQueryService.createQuery().addPathFilter(
                row.getPath().replace(row.getName(), "")).name(
                row.getName()).execute(100);
        RepoPath repoPath = null;
        for (VfsQueryRow vfsQueryRow : result.getAllRows()) {
            RepoPath tempRepoPath = vfsQueryRow.getItem().getRepoPath();
            LocalRepoDescriptor localRepoDescriptor = repoService.localOrCachedRepoDescriptorByKey(
                    tempRepoPath.getRepoKey());
            // If The the descriptor is "jcenter-cached" then return it immediately
            if (localRepoDescriptor != null && tempRepoPath.getRepoKey().equals(
                    jCenterRepo.getKey() + LocalCacheRepoDescriptor.PATH_SUFFIX)) {
                return tempRepoPath;
            }
            // Keep the first repoPath we encounter
            if (repoPath == null && localRepoDescriptor != null) {
                repoPath = tempRepoPath;
            }
        }
        return repoPath;
    }

    @Override
    public RemoteRepoDescriptor getJCenterRepo() {
        String baseUrl = ConstantValues.jCenterUrl.getString();
        List<RemoteRepoDescriptor> remoteRepoDescriptors = repoService.getRemoteRepoDescriptors();
        for (RemoteRepoDescriptor remoteRepoDescriptor : remoteRepoDescriptors) {
            if (remoteRepoDescriptor.getUrl().startsWith(baseUrl)) {
                return remoteRepoDescriptor;
            }
        }
        return null;
    }

    @Override
    public BintrayItemInfo getBintrayItemInfoByChecksum(String sha1) {
        String url = String.format("%ssearch/file/?sha1=%s&subject=bintray&repo=jcenter", getBaseBintrayApiUrl(), sha1);
        return getItemInfoFromCache(url, sha1);
    }

    private void getItemInfoOnBackground(final String url, final String key) {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                InputStream responseStream = null;
                try {
                    GetMethod getMethod = new GetMethod(HttpUtils.encodeQuery(url));
                    HttpClient client = getUserOrSystemApiKeyHttpClient();
                    int status = client.executeMethod(getMethod);
                    BintrayItemInfo result;
                    if (status != HttpStatus.SC_OK) {
                        if (status == HttpStatus.SC_UNAUTHORIZED) {
                            String userName = getCurrentUser().getUsername();
                            log.info("Bintray authentication failure: user {}", userName);
                            result = ITEM_RETRIEVAL_ERROR;
                        } else {
                            result = ITEM_NOT_FOUND;
                        }
                    } else {
                        int rangeLimitTotal = Integer.parseInt(
                                getMethod.getResponseHeader(RANGE_LIMIT_TOTAL).getValue());
                        responseStream = getMethod.getResponseBodyAsStream();
                        List<BintrayItemInfo> listResult = JacksonReader.streamAsValueTypeReference(
                                responseStream, new TypeReference<List<BintrayItemInfo>>() {
                        });
                        BintrayItemSearchResults<BintrayItemInfo> results = new BintrayItemSearchResults<>(
                                listResult,
                                rangeLimitTotal);
                        if (results.getResults().size() > 0) {
                            result = results.getResults().get(0);
                        } else {
                            result = ITEM_NOT_FOUND;
                        }
                    }
                    bintrayItemInfoCache.put(key, result);
                } catch (Exception e) {
                    log.warn("Failure during Bintray fetching package {}: {}", key, e.getMessage());
                    log.debug("Failure during Bintray fetching package {}: {}", key, e);
                    bintrayItemInfoCache.put(key, ITEM_RETRIEVAL_ERROR);
                } finally {
                    IOUtils.closeQuietly(responseStream);
                }
            }
        };
        executor.execute(runnable);
    }

    private HttpClient getUserOrSystemApiKeyHttpClient() {
        HttpClient client;
        if (isUserHasBintrayAuth()) {
            client = createHTTPClient();
        } else if (hasBintraySystemUser()) {
            client = createHTTPClient(new UsernamePasswordCredentials(ConstantValues.bintraySystemUser.getString(),
                    ConstantValues.bintraySystemUserApiKey.getString()));
        } else {
            throw new IllegalStateException("User doesn't have bintray credentials");
        }
        return client;
    }

    @Override
    public BintrayPackageInfo getBintrayPackageInfo(String sha1) {
        BintrayItemInfo bintrayItemInfo = getBintrayItemInfoByChecksum(sha1);
        if (bintrayItemInfo == ITEM_IN_PROCESS) {
            return PACKAGE_IN_PROCESS;
        }
        if (bintrayItemInfo == ITEM_NOT_FOUND) {
            return PACKAGE_NOT_FOUND;
        }
        if (bintrayItemInfo == ITEM_RETRIEVAL_ERROR) {
            return PACKAGE_RETRIEVAL_ERROR;
        }
        return getBintrayPackageInfo(bintrayItemInfo.getOwner(), bintrayItemInfo.getRepo(),
                bintrayItemInfo.getPackage());
    }

    @Override
    public boolean hasBintraySystemUser() {
        return StringUtils.isNotBlank(ConstantValues.bintraySystemUser.getString());
    }

    @Override
    public BintrayPackageInfo getBintrayPackageInfo(String owner, String repo, String packageName) {
        StringBuilder urlBuilder = new StringBuilder(getBaseBintrayApiUrl()).
                append("packages").append("/").
                append(owner).append("/").
                append(repo).append("/").
                append(packageName);

        final String url = urlBuilder.toString();
        BintrayPackageInfo result = getPackageInfoFromCache(url, packageName);
        return result;
    }

    private void getPackageInfoOnBackground(final String url, final String key) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                InputStream responseStream = null;
                try {
                    GetMethod getMethod = new GetMethod(HttpUtils.encodeQuery(url));
                    HttpClient client = getUserOrSystemApiKeyHttpClient();
                    int status = client.executeMethod(getMethod);
                    BintrayPackageInfo result;
                    if (status != HttpStatus.SC_OK) {
                        if (status == HttpStatus.SC_UNAUTHORIZED) {
                            String userName = getCurrentUser().getUsername();
                            log.info("Bintray authentication failure: user {}", userName);
                            result = PACKAGE_RETRIEVAL_ERROR;
                        } else {
                            result = PACKAGE_NOT_FOUND;
                        }
                    } else {
                        responseStream = getMethod.getResponseBodyAsStream();
                        result = JacksonReader.streamAsValueTypeReference(
                                responseStream, new TypeReference<BintrayPackageInfo>() {
                        });
                    }
                    bintrayPackageCache.put(key, result);
                } catch (Exception e) {
                    log.warn("Failure during Bintray fetching package {}: {}", key, e.getMessage());
                    log.debug("Failure during Bintray fetching package {}: {}", key, e);
                    bintrayPackageCache.put(key, PACKAGE_RETRIEVAL_ERROR);
                } finally {
                    IOUtils.closeQuietly(responseStream);
                }
            }
        };
        executor.execute(runnable);
    }

    private BintrayPackageInfo getPackageInfoFromCache(String url, String key) {
        BintrayPackageInfo bintrayPackageInfo = bintrayPackageCache.get(key);
        // Try to get info from bintray if cache is empty or cache contain PACKAGE_RETRIEVAL_ERROR
        if (bintrayPackageInfo == null || bintrayPackageInfo == PACKAGE_RETRIEVAL_ERROR) {
            bintrayPackageCache.put(key, PACKAGE_IN_PROCESS);
            getPackageInfoOnBackground(url, key);
        }
        return bintrayPackageCache.get(key);
    }

    private BintrayItemInfo getItemInfoFromCache(String url, String key) {
        BintrayItemInfo bintrayItemInfo = bintrayItemInfoCache.get(key);
        // Try to get info from bintray if cache is empty or cache contain ITEM_RETRIEVAL_ERROR
        if (bintrayItemInfo == null || bintrayItemInfo == ITEM_RETRIEVAL_ERROR) {
            bintrayItemInfoCache.put(key, ITEM_IN_PROCESS);
            getItemInfoOnBackground(url, key);
        }
        return bintrayItemInfoCache.get(key);
    }

    private InputStream executeGet(String requestUrl, UsernamePasswordCredentials creds)
            throws IOException, BintrayException {
        GetMethod getMethod = new GetMethod(HttpUtils.encodeQuery(requestUrl));
        HttpClient client = createHTTPClient(creds);
        int status = client.executeMethod(getMethod);
        if (status != HttpStatus.SC_OK) {
            throw new BintrayException(getMethod.getStatusText(), getMethod.getStatusCode());
        } else {
            return getMethod.getResponseBodyAsStream();
        }
    }

    private String getBaseBintrayUrl() {
        return PathUtils.addTrailingSlash(ConstantValues.bintrayUrl.getString());
    }

    private String getBaseBintrayApiUrl() {
        return PathUtils.addTrailingSlash(ConstantValues.bintrayApiUrl.getString());
    }

    private PutMethod createPutMethod(FileInfo fileInfo, String requestUrl) {
        PutMethod putMethod = new PutMethod(HttpUtils.encodeQuery(requestUrl));
        InputStream elementInputStream = binaryStore.getBinary(fileInfo.getSha1());
        RequestEntity requestEntity = new InputStreamRequestEntity(elementInputStream, fileInfo.getSize());
        putMethod.setRequestEntity(requestEntity);
        return putMethod;
    }

    private UsernamePasswordCredentials getCurrentUserBintrayCreds() {
        UserInfo userInfo = getCurrentUser();
        String bintrayAuth = userInfo.getBintrayAuth();
        if (StringUtils.isNotBlank(bintrayAuth)) {
            String[] bintrayAuthTokens = StringUtils.split(bintrayAuth, ":");
            if (bintrayAuthTokens.length != 2) {
                throw new IllegalArgumentException("Found invalid Bintray credentials.");
            }

            return new UsernamePasswordCredentials(bintrayAuthTokens[0], bintrayAuthTokens[1]);
        }

        throw new IllegalArgumentException(
                "Couldn't find Bintray credentials, please configure them from the user profile page.");
    }

    private HttpClient createHTTPClient() {
        return createHTTPClient(getCurrentUserBintrayCreds());
    }

    private HttpClient createHTTPClient(UsernamePasswordCredentials creds) {
        ProxyDescriptor proxy = InternalContextHelper.get().getCentralConfig().getDescriptor().getDefaultProxy();

        return new HttpClientConfigurator()
                .hostFromUrl(getBaseBintrayApiUrl())
                .soTimeout(15000)
                .connectionTimeout(15000)
                .retry(0, false)
                .proxy(proxy)
                .authentication(creds)
                .getClient();
    }
}
