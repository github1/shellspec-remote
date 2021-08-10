import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Utils {
   private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
   private static final Map<String, String> FILE_CACHE = new HashMap<>();
   private Utils() {
   }
   static void sleepQuietly(long ms) {
      try {
         Thread.sleep(ms);
      } catch (Exception e) {
         // ignore
      }
   }
   static Path determineBaseDir(Path baseDir) {
      return Utils.pathWithFallback(
              baseDir.resolve("./packages/common/cucumber-bash"), baseDir);
   }
   static Path pathWithFallback(Path initial, Path fallback) {
      return Files.exists(initial) ? initial : fallback;
   }
   static String loadResource(String name) {
      return FILE_CACHE.computeIfAbsent(name, s -> {
         try {
            return IOUtils.resourceToString(name, StandardCharsets.UTF_8,
                    Utils.class.getClassLoader());
         } catch (Exception e) {
            throw new RuntimeException("Failed to load resource: " + name, e);
         }
      });
   }
   static List<String> readFile(Path path) {
      try {
         return IOUtils.readLines(new FileInputStream(
                         path.toFile()),
                 StandardCharsets.UTF_8);
      } catch (IOException e) {
         throw new RuntimeException("Failed to read file " + path, e);
      }
   }
   static void writeFile(Path path, String content) {
      try {
         IOUtils.write(content, new FileOutputStream(path.toFile()),
                 StandardCharsets.UTF_8);
      } catch (IOException e) {
         throw new RuntimeException("Failed to write file " + path, e);
      }
   }
   static void deleteFile(Path path) {
      try {
         if (Files.exists(path)) {
            LOG.debug("Deleting {}", path);
            Files.delete(path);
         }
      } catch (IOException e) {
         throw new RuntimeException("Failed to delete file " + path, e);
      }
   }
   static Stream<Path> findFiles(final Path dir, final Predicate<Path> filter) {
      return Utils.generate(consumer -> findFiles(dir, filter, consumer));
   }
   static void findFiles(Path item, Predicate<Path> filter,
                         Consumer<Path> consumer) {
      try {
         if (Files.isDirectory(item)) {
            Files.list(item).forEach(
                    child -> findFiles(child, filter, consumer));
         } else if (filter.test(item)) {
            consumer.accept(item);
         }
      } catch (IOException e) {
         LOG.error("Failed to load {}", item, e);
      }
   }
   static boolean isNotMacOS() {
      return !System.getProperty("os.name").matches("(?i)^mac os.*");
   }
   static String encodeBase64(String value) {
      return Base64.getEncoder().encodeToString(value.getBytes(
              StandardCharsets.UTF_8));
   }
   static List<Snippet> extractMarkdownSnippets(String type,
                                                List<String> lines) {
      List<Snippet> snippets = new ArrayList<>();
      List<String> buffer = null;
      int lineNumber = 0;
      int startLine = 0;
      for (String line : lines) {
         lineNumber++;
         if (buffer == null && line.trim().equals("```" + type)) {
            startLine = lineNumber;
            buffer = new ArrayList<>();
         } else if (buffer != null) {
            if (line.trim().equals("```")) {
               snippets.add(new Snippet(startLine, String.join("\n", buffer)));
               buffer = null;
            } else {
               buffer.add(line);
            }
         }
      }
      return snippets;
   }
   static RemoteFunctionReplacement replaceRemoteFunctions(Spec spec,
                                                           Map<String, String> environmentVariables,
                                                           HostPort remoteHostPort) {
      return replaceRemoteFunctions(spec, environmentVariables, remoteHostPort,
              (fn) -> fn + UUID.randomUUID());
   }
   static RemoteFunctionReplacement replaceRemoteFunctions(Spec spec,
                                                           Map<String, String> environmentVariables,
                                                           HostPort remoteHostPort,
                                                           Function<String, String> idGenerator) {
      String sessionTempDir = Optional.ofNullable(
              System.getenv("SESSION_TMP_DIR")).orElse("/tmp");
      String remoteFunctionProxyName = "f_invoke_remote";
      Map<String, String> extracted = new LinkedHashMap<>();
      List<Replacement> replacements = new ArrayList<>();
      boolean[] hostAnnotationActive = {false};
      int[] hostAnnotationStartPos = {0};
      String script = spec.getContent();
      Parser.Node node = Parser.parse(script);
      node.accept(new Parser.NodeVisitor() {
         @Override
         public void visit(Parser.Node node) {
            if (node.getType().equals("comment")) {
               if (node.getValue().contains("@OnHost")) {
                  hostAnnotationActive[0] = true;
                  hostAnnotationStartPos[0] = node.getStartPos();
               }
            } else {
               if ("function".equals(node.getType())) {
                  if (hostAnnotationActive[0]) {
                     Optional<Parser.Node> maybeBody = node.getChildren().stream().filter(
                             n -> "script".equals(n.getType())).findFirst();
                     maybeBody.ifPresent(body -> {
                        int startPos = body.getStartPos();
                        int endPos = node.getEndPos();
                        String func = script.substring(
                                body.getChildren().get(0).getStartPos(),
                                body.getEndPos()).trim();
                        String funcID = "F" + idGenerator.apply(
                                node.getValue()).replaceAll("(?i)[^a-z]+", "_");
                        extracted.put(funcID, func);
                        String remoteFunctionProxy = "{\n" + remoteFunctionProxyName + " " + funcID + " $@\n}";
                        replacements.add(
                                new Replacement(hostAnnotationStartPos[0] - 1,
                                        startPos, endPos,
                                        remoteFunctionProxy));
                     });
                  }
               }
               // No longer in a comment, deactivate this
               hostAnnotationActive[0] = false;
            }
         }
      });
      StringBuilder replacedScript = new StringBuilder();
      Replacement previousReplacement = null;
      for (int i = 0; i < replacements.size(); i++) {
         Replacement replacement = replacements.get(i);
         Replacement nextReplacement = replacements.size() > i + 1 ? replacements.get(
                 i + 1) : null;
         int prevEnd = previousReplacement == null ? 0 : previousReplacement.getEndPos();
         int nextStart = nextReplacement == null ? script.length() : (nextReplacement.getAnnotStartPos());
         String start = script.substring(prevEnd, replacement.getStartPos());
         String end = script.substring(replacement.getEndPos(),
                 nextStart);
         replacedScript.append(start).append(
                 replacement.getNewContent()).append(end);
         previousReplacement = replacement;
      }
      StringBuilder finalScript = new StringBuilder();
      environmentVariables.forEach(
              (k, v) -> defineRemoteString("env_var_" + k,
                      String.format("export %s='%s'", k, v),
                      remoteHostPort, finalScript));
      for (Map.Entry<String, String> func : extracted.entrySet()) {
         StringBuilder funcWrapper = new StringBuilder("function ").append(
                 func.getKey()).append("() {\n");
         funcWrapper.append(func.getValue()).append("\n");
         funcWrapper.append("}\n");
         defineRemoteString(func.getKey(), funcWrapper.toString(),
                 remoteHostPort, finalScript);
      }
      String remoteFunctionProxy = loadResource("remote-function-proxy.sh")
              .replace("<SESSION_TEMP_DIR>", sessionTempDir)
              .replace("<GEN_UUID>", genUUID())
              .replace("<NC_SEND>", ncSend(remoteHostPort));
      finalScript.append(remoteFunctionProxy);
      finalScript.append(replacedScript.toString().replaceAll("^#!.*", ""));
      return new RemoteFunctionReplacement(spec, finalScript.toString(),
              extracted);
   }
   private static void defineRemoteString(String id, String script,
                                          HostPort remoteHostPort,
                                          StringBuilder sb) {
      sb.append("echo 'define; ").append(id).append(
              " ").append(Utils.encodeBase64(
              script)).append("' | ").append(Utils.ncSend(
              remoteHostPort)).append("\n");
   }
   static class Snippet {
      private final int startLine;
      private final String content;
      Snippet(int startLine, String content) {
         this.startLine = startLine;
         this.content = content;
      }
      public int getStartLine() {
         return startLine;
      }
      public String getContent() {
         return content;
      }
   }

   static class Spec {
      private final Path path;
      private final String content;
      private final int contentStartLine;
      Spec(Path path, String content) {
         this(path, content, 0);
      }
      Spec(Path path, String content, int contentStartLine) {
         this.path = path;
         this.content = content;
         this.contentStartLine = contentStartLine;
      }
      public Path getPath() {
         return path;
      }
      public String getContent() {
         return content;
      }
      public int getContentStartLine() {
         return contentStartLine;
      }
   }

   static class TempSpec {
      private final Path path;
      private final RemoteFunctionReplacement replacement;
      TempSpec(Path path, RemoteFunctionReplacement replacement) {
         this.path = path;
         this.replacement = replacement;
      }
      public Path getPath() {
         return path;
      }
      public RemoteFunctionReplacement getReplacement() {
         return replacement;
      }
   }

   static class RemoteFunctionReplacement {
      private final Spec spec;
      private final String replacedScript;
      private final Map<String, String> extracted;
      RemoteFunctionReplacement(Spec spec, String replacedScript,
                                Map<String, String> extracted) {
         this.spec = spec;
         this.replacedScript = replacedScript;
         this.extracted = extracted;
      }
      public Spec getSpec() {
         return spec;
      }
      public String getReplacedScript() {
         return replacedScript;
      }
      public Map<String, String> getExtracted() {
         return extracted;
      }
   }
   static <T> Stream<T> generate(
           Consumer<Consumer<T>> producer) {
      AtomicBoolean isDone = new AtomicBoolean(false);
      return Stream.generate(new Supplier<T>() {
                 private final Object initialLock = new Object();
                 private final ConcurrentLinkedQueue<T> items = new ConcurrentLinkedQueue<>();
                 private Thread finder = null;
                 @Override
                 public T get() {
                    if (finder == null) {
                       finder = new Thread(
                               () -> {
                                  producer.accept(
                                          item -> {
                                             items.add(item);
                                             synchronized (initialLock) {
                                                initialLock.notify();
                                             }
                                          });
                                  isDone.set(true);
                               });
                       finder.start();
                       synchronized (initialLock) {
                          try {
                             initialLock.wait();
                          } catch (InterruptedException e) {
                             // ignore
                          }
                       }
                    }
                    while (items.peek() == null && !isDone.get()) {
                       sleepQuietly(100);
                    }
                    return items.poll();
                 }
              })
              .takeWhile(Objects::nonNull);
   }
   static String rewriteFailureLines(
           List<TempSpec> tempSpecs,
           String line) {
      try {
         if (!StringUtils.isEmpty(line)) {
            Pattern failureLinePattern = Pattern.compile(
                    ".* (tmp[^.]+\\.sh):([0-9]+)(.*)");
            Matcher matcher = failureLinePattern.matcher(line);
            if (matcher.matches()) {
               String fileName = matcher.group(1);
               int lineNumber = Integer.parseInt(matcher.group(2));
               String rest = matcher.group(3);
               for (TempSpec tempSpec : tempSpecs) {
                  if (fileName.equals(
                          tempSpec.getPath().getFileName().toString())) {
                     Integer originalLineNumber = null;
                     String[] replacedScriptLines = tempSpec.getReplacement().getReplacedScript().split(
                             "\n");
                     String lineText = replacedScriptLines[lineNumber - 1];
                     String[] originalLines = tempSpec.getReplacement().getSpec().getContent().split(
                             "\n");
                     for (int i = 0; i < originalLines.length; i++) {
                        if (originalLines[i].equals(lineText)) {
                           originalLineNumber = (i + 1) + tempSpec.getReplacement().getSpec().getContentStartLine();
                           break;
                        }
                     }
                     if (line.replaceAll("\u001B\\[[;\\d]*m",
                             "").trim().startsWith("shellspec ")) {
                        return "\u001B[31mshellspec " + tempSpec.getReplacement().getSpec().getPath().getFileName().toString() + ":" + originalLineNumber + "\u001B[0m" + rest;
                     } else {
                        return "\u001B[36m          # " + tempSpec.getReplacement().getSpec().getPath().getFileName().toString() + ":" + originalLineNumber + "\u001B[0m";
                     }
                  }
               }
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
         ;
         throw new RuntimeException(e);
      }
      return line;
   }
   public static String ncSend(HostPort remoteHostPort) {
      List<Object> command = new ArrayList<>();
      command.add("nc");
      if (Utils.isNotMacOS()) {
         command.add("-q");
         command.add("0");
      }
      command.add(remoteHostPort.getHost());
      command.add(remoteHostPort.getPort());
      return command.stream().map(String::valueOf).collect(
              Collectors.joining(" "));
   }
   public static String genUUID() {
      return "uuidgen";
   }
   private static class Replacement {
      private final int annotStartPos;
      private final int startPos;
      private final int endPos;
      private final String newContent;
      private Replacement(int annotStartPos, int startPos, int endPos,
                          String newContent) {
         this.annotStartPos = annotStartPos;
         this.startPos = startPos;
         this.endPos = endPos;
         this.newContent = newContent;
      }
      public int getAnnotStartPos() {
         return annotStartPos;
      }
      public int getStartPos() {
         return startPos;
      }
      public int getEndPos() {
         return endPos;
      }
      public String getNewContent() {
         return newContent;
      }
   }
}
