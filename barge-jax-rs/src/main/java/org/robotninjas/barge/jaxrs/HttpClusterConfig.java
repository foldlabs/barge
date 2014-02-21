/**
 * Copyright 2013-2014 David Rusek <dave dot rusek at gmail dot com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.robotninjas.barge.jaxrs;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.robotninjas.barge.ClusterConfig;
import org.robotninjas.barge.Replica;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

/**
 * Configures a cluster based on HTTP transport.
 * <p>
 * A cluster configuration contains one <em>local</em> instance of a {@link HttpReplica replica} and zero or more
 * remote replicas. A configuration can be built from {@link HttpReplica} instances or simply string representing
 * URIs.
 * </p>
 */
public class HttpClusterConfig implements ClusterConfig {

  private final HttpReplica local;
  private final HttpReplica[] remotes;

  public HttpClusterConfig(HttpReplica local, HttpReplica... remotes) {
    this.local = local;
    this.remotes = remotes;
  }

  public static ClusterConfig from(HttpReplica local, HttpReplica... remotes) {
    return new HttpClusterConfig(local, remotes);
  }

  @Override
  public Replica local() {
    return local;
  }

  @Override
  public Iterable<Replica> remote() {
    return Arrays.<Replica>asList(remotes);
  }

  @Override
  public Replica getReplica(String info) {
    try {
      URI uri = new URI(info);

      if(local.match(uri))
        return local;

      return Iterables.find(remote(), match(uri));
    } catch (URISyntaxException e) {
      throw new RaftHttpException(info + " is not a valid URI, cannot find a corresponding replica",e);
    }
  }

  private Predicate<Replica> match(final URI uri) {
    return new Predicate<Replica>() {
      @Override
      public boolean apply(@Nullable Replica input) {
        return input != null && ((HttpReplica) input).match(uri);
      }
    };
  }

}
