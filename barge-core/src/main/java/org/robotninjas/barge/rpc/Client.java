/**
 * Copyright 2013 David Rusek <dave dot rusek at gmail dot com>
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

package org.robotninjas.barge.rpc;

import com.google.common.base.Functions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import org.jetlang.fibers.Fiber;
import org.robotninjas.barge.RaftFiber;
import org.robotninjas.barge.Replica;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.robotninjas.barge.proto.RaftProto.*;

@Immutable
public class Client {

  @Nonnull
  private final RaftClientProvider clientProvider;
  @Nonnull
  private final Fiber executor;

  @Inject
  public Client(@Nonnull RaftClientProvider clientProvider, @Nonnull @RaftFiber Fiber executor) {
    this.clientProvider = clientProvider;
    this.executor = executor;
  }

  @Nonnull
  public ListenableFuture<RequestVoteResponse> requestVote(@Nonnull final Replica replica, @Nonnull final RequestVote request) {

    checkNotNull(replica);
    checkNotNull(request);

    ListenableFuture<RequestVoteResponse> response = clientProvider.get(replica).requestVote(request);
    return Futures.transform(response, Functions.<RequestVoteResponse>identity(), executor);
  }

  @Nonnull
  public ListenableFuture<AppendEntriesResponse> appendEntries(@Nonnull final Replica replica, @Nonnull final AppendEntries request) {

    checkNotNull(replica);
    checkNotNull(request);

    ListenableFuture<AppendEntriesResponse> response = clientProvider.get(replica).appendEntries(request);
    return Futures.transform(response, Functions.<AppendEntriesResponse>identity(), executor);
  }

}
