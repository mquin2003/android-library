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

import android.net.Uri;

import com.nextcloud.common.NextcloudAuthenticator;
import com.nextcloud.common.NextcloudClient;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.network.WebdavEntry;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.webdav.NCFavorite;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.jackrabbit.webdav.client.methods.PropPatchMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.bitfire.dav4jvm.DavResource;
import at.bitfire.dav4jvm.Property;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * Favorite or unfavorite a file.
 */
public class ToggleFavoriteRemoteOperation extends RemoteOperation {
    private boolean makeItFavorited;
    private String filePath;

    public ToggleFavoriteRemoteOperation(boolean makeItFavorited, String filePath) {
        this.makeItFavorited = makeItFavorited;
        this.filePath = filePath;
    }

    @Override
    protected RemoteOperationResult run(OwnCloudClient client) {
        RemoteOperationResult result = null;
        PropPatchMethod propPatchMethod = null;

        DavPropertySet newProps = new DavPropertySet();
        DavPropertyNameSet removeProperties = new DavPropertyNameSet();

        if (makeItFavorited) {
            DefaultDavProperty<String> favoriteProperty = new DefaultDavProperty<>("oc:favorite", "1",
                    Namespace.getNamespace(WebdavEntry.NAMESPACE_OC));
            newProps.add(favoriteProperty);
        } else {
            removeProperties.add("oc:favorite", Namespace.getNamespace(WebdavEntry.NAMESPACE_OC));
        }

        String webDavUrl = client.getDavUri().toString();
        String encodedPath = (client.getUserId() + Uri.encode(filePath)).replace("%2F", "/");
        String fullFilePath = webDavUrl + "/files/" + encodedPath;

        try {
            propPatchMethod = new PropPatchMethod(fullFilePath, newProps, removeProperties);
            int status = client.executeMethod(propPatchMethod);

            boolean isSuccess = (status == HttpStatus.SC_MULTI_STATUS || status == HttpStatus.SC_OK);

            if (isSuccess) {
                result = new RemoteOperationResult(true, status, propPatchMethod.getResponseHeaders());
            } else {
                client.exhaustResponse(propPatchMethod.getResponseBodyAsStream());
                result = new RemoteOperationResult(false, status, propPatchMethod.getResponseHeaders());
            }
        } catch (IOException e) {
            result = new RemoteOperationResult(e);
        } finally {
            if (propPatchMethod != null) {
                propPatchMethod.releaseConnection();  // let the connection available for other methods
            }
        }

        return result;
    }

    @Override
    public RemoteOperationResult run(NextcloudClient client) {
        RemoteOperationResult<Boolean> result;

        List<Property.Name> removeProperties = new ArrayList<>();
        Map<Property.Name, String> newProperties = new HashMap<>();

        // disable redirect
        OkHttpClient disabledRedirectClient = client.getClient()
                .newBuilder()
                .followRedirects(false)
                .authenticator(new NextcloudAuthenticator(client.getCredentials(), "Authorization"))
                .build();

        if (makeItFavorited) {
            newProperties.put(NCFavorite.NAME, "1");
        } else {
            removeProperties.add(NCFavorite.NAME);
        }

        String webDavUrl = client.getDavUri().toString();
        String encodedPath = (client.getUserId() + Uri.encode(filePath)).replace("%2F", "/");
        String fullFilePath = webDavUrl + "/files/" + encodedPath;

        boolean resultCode = false;

        new DavResource(disabledRedirectClient,
                HttpUrl.get(fullFilePath))
                .proppatch(newProperties, removeProperties, (response, hrefRelation) -> {
                    //resultCode = response.isSuccess();
                });

        result = new RemoteOperationResult<>(RemoteOperationResult.ResultCode.OK);

        return result;
    }
}
