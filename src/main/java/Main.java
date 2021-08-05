import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CommandLine.Command(name = "cucumber-bash", mixinStandardHelpOptions = true, version = "cucumber-bash 1.0",
        description = "Runs cucumber with bash step definitions")
class Main implements Callable<Integer> {
   private static final Logger LOG = LoggerFactory.getLogger(Main.class);
   private static final Pattern PROBABLY_SHELLSPEC = Pattern.compile(
           "(Context|Describe|It|When)", Pattern.MULTILINE | Pattern.DOTALL);
   @CommandLine.Parameters(index = "0", description = "[file or directory]")
   private File script;
   @CommandLine.Option(names = {"-r", "--remote-host"}, description = "Remote bridge host")
   private String remoteBridgeHostPort;
   public static void main(String... args) {
      System.exit(new Main().execute(args));
   }
   public int execute(String... args) {
      return new CommandLine(new Main()).execute(args);
   }
   @Override
   public Integer call() {
      int status = -1;
      Path userDir = Paths.get(System.getProperty("user.dir"));
      List<Utils.TempSpec> tempScripts = new ArrayList<>();
      try {
         List<Utils.Spec> specs = new ArrayList<>();
         if (script.isFile()) {
            acceptFile(script.toPath(), specs);
         } else if (script.isDirectory()) {
            Utils.findFiles(script.toPath(),
                    item -> item.toString().endsWith(
                            "_spec.sh") || item.toString().endsWith(
                            ".md")).forEach(item -> acceptFile(item, specs));
         }
         specs.stream().map(spec -> Utils.replaceRemoteFunctions(
                 spec,
                 HostPort.fromString(remoteBridgeHostPort))).forEach(
                 replaced -> {
                    String replacedScript = replaced.getReplacedScript();
                    LOG.debug("Using replaced script {}", replacedScript);
                    Path tempScriptPath = userDir.resolve(
                            "tmp" + UUID.randomUUID() + "_spec.sh");
                    tempScripts.add(
                            new Utils.TempSpec(tempScriptPath, replaced));
                    Utils.writeFile(tempScriptPath, replacedScript);
                 });

         List<String> command = new ArrayList<>();
         command.add("shellspec");
         command.add("--shell");
         command.add("bash");
         command.add("--color");
         if (tempScripts.size() == 1) {
            command.add(
                    tempScripts.get(0).getPath().toAbsolutePath().toString());
         } else {
            command.add(userDir.toAbsolutePath().toString());
         }
         ScriptRuntime runtime = new ScriptRuntime();
         status = runtime.execute(
                 command.toArray(new String[0]),
                 outputLine -> {
                    String line = Utils.rewriteFailureLines(tempScripts,
                            outputLine.getLine());
                    if (ScriptRuntimeOutputStreamType.STDOUT.equals(
                            outputLine.getStream())) {
                       System.out.println(line);
                    } else if (ScriptRuntimeOutputStreamType.STDERR.equals(
                            outputLine.getStream())) {
                       System.err.println(line);
                    }
                 }).waitFor();
         // Wait a bit for the output to catch up
         Thread.sleep(500);
      } catch (Exception e) {
         LOG.error("Unexpected error", e);
      } finally {
         tempScripts.stream().map(Utils.TempSpec::getPath).forEach(
                 Utils::deleteFile);
      }
      return status;
   }
   private static void acceptFile(Path item, List<Utils.Spec> specs) {
      if (item.toString().endsWith(".md")) {
         specs.addAll(Utils.extractMarkdownSnippets("bash",
                         Utils.readFile(item)).stream().filter(
                         snippet -> snippet.getContent().contains(
                                 "Describe") || snippet.getContent().contains(
                                 "When") || snippet.getContent().contains("It"))
                 .map(snippet -> new Utils.Spec(item, snippet.getContent(),
                         snippet.getStartLine()))
                 .collect(
                         Collectors.toList()));
      } else if (item.toString().endsWith(".sh")) {
         specs.add(
                 new Utils.Spec(item, String.join("\n", Utils.readFile(item))));
      }
   }
}