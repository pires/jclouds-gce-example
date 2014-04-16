/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.pires.example;

import com.google.common.base.Charsets;
import com.google.common.base.Predicates;
import static com.google.common.base.Predicates.not;
import com.google.common.collect.ImmutableSet;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getOnlyElement;
import com.google.common.io.Files;
import com.google.inject.Module;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.RunScriptOnNodesException;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import static org.jclouds.compute.options.TemplateOptions.Builder.overrideLoginCredentials;
import static org.jclouds.compute.options.TemplateOptions.Builder.runScript;
import static org.jclouds.compute.predicates.NodePredicates.TERMINATED;
import static org.jclouds.compute.predicates.NodePredicates.inGroup;
import static org.jclouds.compute.predicates.NodePredicates.withIds;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.scriptbuilder.domain.Statement;
import static org.jclouds.scriptbuilder.domain.Statements.exec;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This application demonstrates the use of the jclouds-gce by creating TODO
 * something Usage is: java MainApp accesskeyid secretkey command name where
 * command in create destroy
 */
public class App {

  public static enum Action {

    ADD,
    RUN,
    EXEC,
    DESTROY,
    LISTNODES,
    LISTIMAGES;

  }

  private static final Logger log = LoggerFactory
      .getLogger(App.class.getName());

  private static final String PROVIDER_GCE = "google-compute-engine";

  private CmdLineParser parser;

  @Option(name = "--account", usage = "Project service account email")
  private String account = "your-project-service-account-email@developer.gserviceaccount.com";

  @Option(name = "--pk", usage = "Private key path  ")
  private String pkPath = "/path/to/private-key.pem";

  @Option(name = "--help", usage = "Show help")
  private boolean help = false;

  private Action action;
  // optional stuff
  private String groupName, command, filePath, nodeId;

  @Argument
  private List<String> arguments = new ArrayList<>();

  public static void main(String[] args) throws Exception {
    new App().execute(args);
  }

  public void execute(String... args) throws Exception {
    parser = new CmdLineParser(this);
    parser.setUsageWidth(80);
    try {
      parser.parseArgument(args);
      action = Action.valueOf(args[4].toUpperCase());
      switch (action) {
      case ADD:
        groupName = args[5];
        break;
      case EXEC:
        groupName = args[5];
        command = args[6];
        break;
      case RUN:
        groupName = args[5];
        filePath = args[6];
        break;
      case DESTROY:
        groupName = args[5];
        nodeId = args[6];
      }
    } catch (Exception e) {
      log.error("There was an error while parsing parameters.. ", e);
      printUsage();
      System.exit(1);
    }

    // show help, if requested
    if (help) {
      printUsage();
      System.exit(1);
    } else {
      // get private-key
      final String credentials = getPrivateKeyFromFile(pkPath);
      // prepare credentials
      LoginCredentials login = getLoginForCommandExecution(action);
      ComputeService compute = initComputeService(account, credentials);

      // go
      int error = 0;
      try {
        switch (action) {
        case ADD:
          log.info(">> adding node to group {}", groupName);

          // Default template chooses the smallest size on an operating system
          // that tested to work with java, which tends to be Ubuntu or CentOS
          TemplateBuilder templateBuilder = compute.templateBuilder();
          templateBuilder.fromImage(compute
              .getImage("debian-7-wheezy-v20140408"));
          templateBuilder.locationId("europe-west1-a");
          templateBuilder.fastest();

          // note this will create a user with the same name as you on the
          // node. ex. you can connect via ssh publicip
          Statement bootInstructions = AdminAccess.standard();
          templateBuilder.options(runScript(bootInstructions));

          NodeMetadata node = getOnlyElement(compute.createNodesInGroup(
              groupName, 1, templateBuilder.build()));
          log.info("<< node {}: {}", node.getId(),
              concat(node.getPrivateAddresses(), node.getPublicAddresses()));
          break;

        case EXEC:
          log.info(">> running {} on group {} as {}", command, groupName,
              login.identity);

          // when you run commands, you can pass options to decide whether to
          // run it as root, supply or own credentials vs from cache, and wrap
          // in an init script vs directly invoke
          Map<? extends NodeMetadata, ExecResponse> responses = compute
              .runScriptOnNodesMatching(//
                  inGroup(groupName), // predicate used to select nodes
                  exec(command), // what you actually intend to run
                  overrideLoginCredentials(login) // use my local user &
                      // ssh key
                      .runAsRoot(false) // don't attempt to run as root (sudo)
                      .wrapInInitScript(false));// run command directly

          for (Entry<? extends NodeMetadata, ExecResponse> response : responses
              .entrySet()) {
            log.info(
                "<< node {}: {}",
                response.getKey().getId(),
                concat(response.getKey().getPrivateAddresses(), response
                    .getKey().getPublicAddresses()));
            log.info("<<     {}", response.getValue());
          }
          break;

        case RUN:
          final File file = new File("TODO_FILE_PATH");
          log.info(">> running {} on group {} as {}", file, groupName,
              login.identity);

          // when running a sequence of commands, you probably want to have
          // jclouds use the default behavior,
          // which is to fork a background process.
          responses = compute.runScriptOnNodesMatching(
          //
              inGroup(groupName), Files.toString(file, Charsets.UTF_8), // passing
              // in a
              // string
              // with
              // the contents of the file
              overrideLoginCredentials(login).runAsRoot(false).nameTask(
                  "_" + file.getName().replaceAll("\\..*", ""))); // ensuring
          // task name
          // isn't
          // the same as the file so status checking works properly

          for (Entry<? extends NodeMetadata, ExecResponse> response : responses
              .entrySet()) {
            log.info(
                "<< node {}: {}",
                response.getKey().getId(),
                concat(response.getKey().getPrivateAddresses(), response
                    .getKey().getPublicAddresses()));
            log.info("<<     {}", response.getValue());
          }
          break;

        case DESTROY:
          log.info(">> destroying node [{}] in group [{}]", nodeId, groupName);
          // you can use predicates to select which nodes you wish to destroy.
          Set<? extends NodeMetadata> destroyed = compute
              .destroyNodesMatching(Predicates.<NodeMetadata> and(
                  not(TERMINATED), inGroup(groupName), withIds(nodeId)));
          log.info("<< destroyed noded {}", destroyed);
          break;
        case LISTIMAGES:
          // list images
          Set<? extends Image> images = compute.listImages();
          log.info(">> No of images {}", images.size());
          for (Image img : images) {
            log.info(">>>>  {}", img);
          }
          break;

        case LISTNODES:
          Set<? extends ComputeMetadata> nodes = compute.listNodes();
          log.info(">> No of nodes/instances {}", nodes.size());
          for (ComputeMetadata nodeData : nodes) {
            log.info(">>>> {}", (NodeMetadata) nodeData);
          }

          //
          break;
        }
      } catch (RunNodesException e) {
        log.error("error adding node to group {}", groupName, e);
        error = 1;
      } catch (RunScriptOnNodesException e) {
        log.error("error executing {} on group {}", command, groupName, e);
        error = 1;
      } catch (Exception e) {
        log.error("error: ", e);
        error = 1;
      } finally {
        compute.getContext().close();
        System.exit(error);
      }
    }

  }

  /**
   * Reads private-key from PEM file.
   * 
   * @param filename
   * @return the private-key text
   * @throws IOException
   *           if the file can't be read
   */
  private static String getPrivateKeyFromFile(final String filename)
      throws IOException {
    try {
      return Files.toString(new File(filename), Charsets.UTF_8);
    } catch (IOException e) {
      log.error("Exception reading private key from {}", filename, e);
      throw e;
    }
  }

  private ComputeService initComputeService(String identity, String credential) {
    // example of injecting a ssh implementation
    Iterable<Module> modules = ImmutableSet.<Module> of(
        new SshjSshClientModule(), new SLF4JLoggingModule(),
        new EnterpriseConfigurationModule());

    ContextBuilder builder = ContextBuilder.newBuilder(PROVIDER_GCE)
        .credentials(identity, credential).modules(modules);

    log.info(">> initializing {}", builder.getApiMetadata());

    return builder.buildView(ComputeServiceContext.class).getComputeService();
  }

  private LoginCredentials getLoginForCommandExecution(Action action)
      throws Exception {
    try {
      String user = System.getProperty("user.name");
      String privateKey = Files.toString(
          new File(System.getProperty("user.home") + "/.ssh/id_rsa"),
          Charsets.UTF_8);
      return LoginCredentials.builder().user(user).privateKey(privateKey)
          .build();
    } catch (Exception e) {
      log.error("There was an error while reading ssh key.");
      throw e;
    }
  }

  private void printUsage() {
    System.err.println("java -jar [jarfile] [options...] arguments...");
    parser.printUsage(System.err);
    System.err.println();
    System.err.println("Examples:");
    System.err.println("java -jar [jarfile] [options] listnodes");
    System.err.println("java -jar [jarfile] [options] add mygroup");
    System.err.println("java -jar [jarfile] [options] remove mygroup nodeid");
    System.err.println("java -jar [jarfile] [options] exec mygroup mycommand");
    System.err.println("java -jar [jarfile] [options] run mygroup myfile");
  }

}
