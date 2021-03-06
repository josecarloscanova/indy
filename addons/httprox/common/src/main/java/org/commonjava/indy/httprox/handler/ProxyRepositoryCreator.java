/**
 * Copyright (C) 2011 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.httprox.handler;

import org.commonjava.indy.model.core.RemoteRepository;
import org.commonjava.indy.subsys.http.util.UserPass;
import org.commonjava.indy.subsys.template.ScriptEngine;
import org.commonjava.indy.util.UrlInfo;
import org.slf4j.Logger;

/**
 * Responsible for creating new {@link RemoteRepository} instances for use with the HTTProx proxy add-on.
 * This interface will be implemented by a Groovy script, and accessed by way of the
 * {@link org.commonjava.indy.subsys.template.ScriptEngine#parseStandardScriptInstance(ScriptEngine.StandardScriptType, String, Class)} method.
 *
 * Created by jdcasey on 8/17/16.
 */
public interface ProxyRepositoryCreator
{
    RemoteRepository create( String name, String baseUrl, UrlInfo info, UserPass up, Logger logger );
}
