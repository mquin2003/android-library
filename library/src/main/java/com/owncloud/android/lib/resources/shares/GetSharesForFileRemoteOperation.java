/* ownCloud Android Library is available under MIT license
 *   @author masensio
 *   @author David A. Velasco
 *   Copyright (C) 2015 ownCloud Inc.
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

package com.owncloud.android.lib.resources.shares;

import com.nextcloud.common.NextcloudClient;
import com.nextcloud.operations.GetMethod;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;

import org.apache.commons.httpclient.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Provide a list shares for a specific file.
 * The input is the full path of the desired file.
 * The output is a list of everyone who has the file shared with them.
 */
public class GetSharesForFileRemoteOperation extends RemoteOperation<List<OCShare>> {

    private static final String TAG = GetSharesForFileRemoteOperation.class.getSimpleName();

    private static final String PARAM_PATH = "path";
    private static final String PARAM_RESHARES = "reshares";
    private static final String PARAM_SUBFILES = "subfiles";

    private final String mRemoteFilePath;
    private final boolean mReshares;
    private final boolean mSubfiles;

    /**
     * Constructor
     *
     * @param remoteFilePath Path to file or folder
     * @param reshares       If set to false (default), only shares owned by the current user are
     *                       returned.
     *                       If set to true, shares owned by any user from the given file are returned.
     * @param subfiles       If set to false (default), lists only the folder being shared
     *                       If set to true, all shared files within the folder are returned.
     */
    public GetSharesForFileRemoteOperation(String remoteFilePath, boolean reshares, boolean subfiles) {
        mRemoteFilePath = remoteFilePath;
        mReshares = reshares;
        mSubfiles = subfiles;
    }

    @Override
    public RemoteOperationResult<List<OCShare>> run(NextcloudClient client) {
        RemoteOperationResult<List<OCShare>> result;
        int status;

        GetMethod get = null;

        try {
            // get method
            get = new com.nextcloud.operations.GetMethod(client.getBaseUri() + ShareUtils.SHARING_API_PATH, true);

            // add parameters to get method
            get.setQueryString(Map.of(
                    PARAM_PATH, mRemoteFilePath,
                    PARAM_RESHARES, String.valueOf(mReshares),
                    PARAM_SUBFILES, String.valueOf(mSubfiles)
                                     ));

            status = client.execute(get);

            if (isSuccess(status)) {
                String response = get.getResponseBodyAsString();

                // Parse xml response and obtain the list of shares
                ShareToRemoteOperationResultParser parser = new ShareToRemoteOperationResultParser(
                    new ShareXMLParser()
                );
                parser.setServerBaseUri(client.getBaseUri());
                result = parser.parse(response);

                if (result.isSuccess()) {
                    Log_OC.d(TAG, "Got " + result.getResultData().size() + " shares");
                }

            } else {
                result = new RemoteOperationResult<>(false, get);
            }

        } catch (Exception e) {
            result = new RemoteOperationResult<>(e);
            Log_OC.e(TAG, "Exception while getting shares", e);

        } finally {
            if (get != null) {
                get.releaseConnection();
            }
        }
        return result;
    }

    private boolean isSuccess(int status) {
        return (status == HttpStatus.SC_OK);
    }

}
