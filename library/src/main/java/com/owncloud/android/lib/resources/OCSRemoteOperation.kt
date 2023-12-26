/* Nextcloud Android Library is available under MIT license
 *
 *   @author Bartosz Przybylski
 *   Copyright (C) 2018 Bartosz Przybylski
 *   Copyright (C) 2018 Nextcloud GmbH
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
package com.owncloud.android.lib.resources

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.nextcloud.common.OkHttpMethodBase
import com.owncloud.android.lib.common.operations.RemoteOperation
import org.apache.commons.httpclient.HttpMethodBase
import java.io.IOException

abstract class OCSRemoteOperation<T> : RemoteOperation<T>() {
    private val gson = Gson()

    fun <T> getServerResponse(method: HttpMethodBase, type: TypeToken<T>): T? {
        val response = method.responseBodyAsString
        val element = JsonParser.parseString(response).asJsonObject
        return gson.fromJson(element, type.type)
    }

    fun <T> getServerResponse(method: OkHttpMethodBase, type: TypeToken<T>): T? {
        val response = method.getResponseBodyAsString()
        val element = JsonParser.parseString(response).asJsonObject
        return gson.fromJson(element, type.type)
    }
}
