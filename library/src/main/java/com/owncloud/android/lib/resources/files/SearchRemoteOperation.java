/* Nextcloud Android Library is available under MIT license
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic
 *   Copyright (C) 2017 Nextcloud GmbH
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 *
 */

package com.owncloud.android.lib.resources.files;

import com.nextcloud.common.NextcloudClient;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.WebDavFileUtils;
import com.owncloud.android.lib.resources.files.model.RemoteFile;
import com.owncloud.android.lib.resources.status.OCCapability;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;
import org.apache.jackrabbit.webdav.search.SearchInfo;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.w3c.dom.Document;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import at.bitfire.dav4jvm.DavResource;
import at.bitfire.dav4jvm.Response;
import okhttp3.HttpUrl;

/**
 * Remote operation performing the search in the Nextcloud server.
 */
public class SearchRemoteOperation extends RemoteOperation<List<RemoteFile>> {


    public enum SearchType {
        FILE_SEARCH, // search by name
        FAVORITE_SEARCH, // get all favorite files/folder
        RECENTLY_MODIFIED_SEARCH, // get files/folders that were modified within last 7 days, ordered descending by time
        PHOTO_SEARCH, // gets all files with mimetype "image/%"
        /**
         * @deprecated unused, to be removed in a future version
         */
        @Deprecated
        SHARED_SEARCH, // show all shares
        GALLERY_SEARCH, // combined photo and video
        FILE_ID_SEARCH, // search one file specified by file id
        CONTENT_TYPE_SEARCH,
        RECENTLY_ADDED_SEARCH,
        SHARED_FILTER
    }

    private final String searchQuery;
    private final SearchType searchType;
    private final boolean filterOutFiles;
    private int limit;
    private long timestamp = -1;
    private final OCCapability capability;
    private Long startDate = null;
    private Long endDate = null;

    public SearchRemoteOperation(String query,
                                 SearchType searchType,
                                 boolean filterOutFiles,
                                 final OCCapability capability) {
        this.searchQuery = query;
        this.searchType = searchType;
        this.filterOutFiles = filterOutFiles;
        this.capability = capability;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setStartDate(Long startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(Long endDate) {
        this.endDate = endDate;
    }

    @Override
    protected RemoteOperationResult<List<RemoteFile>> run(OwnCloudClient client) {
        RemoteOperationResult<List<RemoteFile>> result;
        NcSearchMethod searchMethod = null;
        OptionsMethod optionsMethod;

        String webDavUrl = client.getDavUri().toString();
        optionsMethod = new OptionsMethod(webDavUrl);

        try {
            searchMethod = new NcSearchMethod(webDavUrl,
                    new SearchInfo("NC",
                            Namespace.XMLNS_NAMESPACE,
                            searchQuery),
                    searchType,
                    getClient().getUserIdPlain(),
                    timestamp,
                    limit,
                    filterOutFiles,
                    capability,
                    startDate,
                    endDate);

            int status = client.executeMethod(searchMethod);

            // check and process response
            boolean isSuccess = (status == HttpStatus.SC_MULTI_STATUS || status == HttpStatus.SC_OK);

            if (isSuccess) {
                // get data from remote folder
                MultiStatus dataInServer = searchMethod.getResponseBodyAsMultiStatus();
                WebDavFileUtils webDavFileUtils = new WebDavFileUtils();
                ArrayList<RemoteFile> mFolderAndFiles = webDavFileUtils.readData(dataInServer,
                        client.getFilesDavUri(),
                        false,
                        true);

                // Result of the operation
                result = new RemoteOperationResult<>(true, status, searchMethod.getResponseHeaders());
                // Add data to the result
                if (result.isSuccess()) {
                    result.setResultData(mFolderAndFiles);
                }
            } else {
                // synchronization failed
                client.exhaustResponse(searchMethod.getResponseBodyAsStream());
                result = new RemoteOperationResult<>(false, status, searchMethod.getResponseHeaders());
            }
        } catch (Exception e) {
            result = new RemoteOperationResult<>(e);
        } finally {
            if (searchMethod != null) {
                searchMethod.releaseConnection();  // let the connection available for other methods
            }

            optionsMethod.releaseConnection();
        }
        return result;
    }

    @Override
    public RemoteOperationResult run(NextcloudClient client) {
        RemoteOperationResult<List<RemoteFile>> result;
        NcSearchMethod searchMethod = null;

        String webDavUrl = client.getDavUri().toString();

        SearchInfo searchInfo = new SearchInfo("NC",
                Namespace.XMLNS_NAMESPACE,
                searchQuery);

        try {
            searchMethod = new NcSearchMethod(webDavUrl,
                    searchInfo,
                    searchType,
                    getClientNew().getUserIdPlain(),
                    timestamp,
                    limit,
                    filterOutFiles,
                    capability,
                    startDate,
                    endDate);

            Document searchDocument = searchMethod.getDocumentQuery(searchInfo);
            String searchString = transformDocumentToString(searchDocument);

            ArrayList<Response> responses = new ArrayList<>();

            new DavResource(
                    client.disabledRedirectClient(),
                    HttpUrl.get(client.getDavUri().toString()))
                    .search(searchString, (response, hrefRelation) -> {
                        responses.add(response);
                    });

            // get data from remote folder
            WebDavFileUtils webDavFileUtils = new WebDavFileUtils();

            ArrayList<RemoteFile> list = new ArrayList<>();
            for (Response response : responses) {
                list.add(webDavFileUtils.parseResponse(response, client.getFilesDavUri()));
            }

            // Result of the operation
            result = new RemoteOperationResult<>(
                    true,
                    HttpStatus.SC_OK,
                    searchMethod.getResponseHeaders());
            // Add data to the result
            if (result.isSuccess()) {
                result.setResultData(list);
            }
        } catch (Exception e) {
            result = new RemoteOperationResult<>(e);
        } finally {
            if (searchMethod != null) {
                searchMethod.releaseConnection();  // let the connection available for other methods
            }
        }
        return result;
    }

    public String transformDocumentToString(Document document) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans = tf.newTransformer();
        StringWriter sw = new StringWriter();
        trans.transform(new DOMSource(document), new StreamResult(sw));
        return sw.toString();
    }
}

