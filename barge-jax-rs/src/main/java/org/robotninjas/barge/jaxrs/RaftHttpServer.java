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

import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

import com.google.inject.Guice;
import com.google.inject.Injector;

import com.sun.net.httpserver.HttpServer;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import org.robotninjas.barge.ClusterConfig;
import org.robotninjas.barge.StateMachine;
import org.robotninjas.barge.state.Raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.ByteBuffer;

import java.util.List;
import java.util.logging.Level;

import javax.annotation.Nonnull;


/**
 * A dedicated server for an instance of Raft.
 */
public class RaftHttpServer {

  private static final Logger logger = LoggerFactory.getLogger(RaftHttpServer.class);

  private static final String help = "Usage: java -jar barge.jar [options] <server index>\n" +
      "Options:\n" +
      " -h                       : Displays this help message\n" +
      " -t <timeout in ms>       : Sets timeout in ms (default: 1000ms)\n" +
      " -c <configuration file>  : Use given configuration file for cluster configuration\n" +
      "                            This file is a simple property file with indices as keys and URIs as values, eg. like\n\n" +
      "                              0=http://localhost:1234\n" +
      "                              1=http://localhost:3456\n" +
      "                              2=http://localhost:4567\n\n" +
      "                            Default is './barge.conf'\n" +
      "<server index>            : Index of this server in the cluster configuration\n";

  private final int serverIndex;
  private final URI[] uris;
  private final int timeout;

  private HttpServer httpServer;

  public RaftHttpServer(int serverIndex, URI[] uris, int timeout) {
    this.serverIndex = serverIndex;
    this.uris = uris;
    this.timeout = timeout;
  }

  public void stop(int timeout) {
    httpServer.stop(timeout);
  }

  public static void main(String[] args) throws IOException, URISyntaxException {
    muteJul();

    File clusterConfiguration = new File("barge.conf");
    int index = -1;
    int timeout = 1000;

    for (int i = 0; i < args.length; i++) {

      if (args[i].equals("-c")) {
        clusterConfiguration = new File(args[++i]);
      } else if (args[i].equals("-h")) {
        usage();
        System.exit(0);
      } else if (args[i].equals("-t")) {
        timeout = Integer.parseInt(args[++i]);
      } else {

        try {
          index = Integer.parseInt(args[i].trim());
        } catch (NumberFormatException e) {
          usage();
          System.exit(1);
        }
      }
    }

    if (index == -1) {
      usage();
      System.exit(1);
    }

    URI[] uris = readConfiguration(clusterConfiguration);

    RaftHttpServer server = new RaftHttpServer(index, uris, timeout).start();

    //noinspection ResultOfMethodCallIgnored
    System.in.read();
    server.stop(1);

    System.out.println("Bye!");
    System.exit(0);
  }

  private static void muteJul() {
    java.util.logging.Logger.getLogger("").setLevel(Level.ALL);
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private static URI[] readConfiguration(File clusterConfiguration) throws IOException, URISyntaxException {
    List<URI> uris = Lists.newArrayList();

    int lineNumber = 1;

    for (String line : CharStreams.readLines(new FileReader(clusterConfiguration))) {
      String[] pair = line.split("=");

      if (pair.length != 2)
        logger.error("Invalid cluster configuration at line {}, ignoring", lineNumber);

      uris.add(Integer.parseInt(pair[0].trim()), new URI(pair[1].trim()));
    }

    return uris.toArray(new URI[uris.size()]);
  }

  private static void usage() {
    System.out.println(help);
  }


  public RaftHttpServer start() throws IOException {
    ClusterConfig clusterConfig = HttpClusterConfig.from(new HttpReplica(uris[serverIndex]),
      selectRemoteReplicas(serverIndex, uris));

    File logDir = new File("log" + serverIndex);

    if (!logDir.exists() && !logDir.mkdirs())
      logger.warn("failed to create directories for storing logs, bad things will happen");

    StateMachine stateMachine = new StateMachine() {
      int i = 0;

      @Override
      public Object applyOperation(@Nonnull ByteBuffer entry) {
        return i++;
      }
    };

    final JaxRsRaftModule raftModule = new JaxRsRaftModule(clusterConfig, logDir, stateMachine, timeout);

    final Injector injector = Guice.createInjector(raftModule);

    ResourceConfig resourceConfig = new ResourceConfig();

    Binder binder = new AbstractBinder() {
      @Override
      protected void configure() {
        bindFactory(new Factory<Raft>() {
              @Override
              public Raft provide() {
                return injector.getInstance(Raft.class);
              }

              @Override
              public void dispose(Raft raft) {
              }
            }).to(Raft.class);
      }
    };

    resourceConfig.register(BargeResource.class);
    resourceConfig.register(Jackson.customJacksonProvider());
    resourceConfig.register(binder);

    this.httpServer = JdkHttpServerFactory.createHttpServer(uris[serverIndex], resourceConfig);

    return this;
  }

  private HttpReplica[] selectRemoteReplicas(int serverIndex, URI[] uris) {
    int numberOfReplicas = uris.length;
    HttpReplica[] result = new HttpReplica[numberOfReplicas - 1];

    for (int i = 1; i < numberOfReplicas; i++) {
      result[i - 1] = new HttpReplica(uris[(serverIndex + i) % numberOfReplicas]);
    }

    return result;
  }
}
