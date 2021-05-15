import org.apache.commons.io.IOUtils;
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
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Utils {
   private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
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
   static List<String> extractMarkdownSnippets(String type,
                                               List<String> lines) {
      List<String> snippets = new ArrayList<>();
      List<String> buffer = null;
      for (String line : lines) {
         if (buffer == null && line.trim().equals("```" + type)) {
            buffer = new ArrayList<>();
         } else if (buffer != null) {
            if (line.trim().equals("```")) {
               snippets.add(String.join("\n", buffer));
               buffer = null;
            } else {
               buffer.add(line);
            }
         }
      }
      return snippets;
   }
   static RemoteFunctionReplacement replaceRemoteFunctions(String script,
                                                           HostPort remoteHostPort) {
      String sessionTempDir = Optional.ofNullable(
              System.getenv("SESSION_TMP_DIR")).orElse("/tmp");
      Map<String, String> extracted = new LinkedHashMap<>();
      String[] holder = {script};
      boolean[] hostAnnotationActive = {false};
      int[] offset = {0};
      Parser.Node node = Parser.parse(script);
      node.accept(new Parser.NodeVisitor() {
         @Override
         public void visit(Parser.Node node) {
            if (node.getType().equals("comment")) {
               if (node.getValue().contains("@OnHost")) {
                  hostAnnotationActive[0] = true;
               }
            } else {
               if ("function".equals(node.getType())) {
                  if (hostAnnotationActive[0]) {
                     Parser.Node body = node.getLastChild();
                     int startPos = body.getStartPos() + offset[0];
                     int endPos = body.getLastChild().getStartPos() + offset[0];
                     int origLength = endPos - startPos;
                     String before = holder[0].substring(0, startPos);
                     String func = holder[0].substring(startPos,
                             endPos + 1);
                     String funcID = "F" + UUID.randomUUID().toString().replace(
                             "-", "_");
                     func = func.trim().replaceAll("^\\{\n|\n}$", "");
                     extracted.put(funcID, func);
                     String beforeChange = holder[0];
                     String responseFile = sessionTempDir + "/responsefile${l_UUID}";
                     String placeHolder = "{\n" +
                             "local l_UUID=$(" +
                             genUUID() +
                             ")\n" +
                             "echo 'invoke; '${l_UUID}' " +
                             funcID +
                             " '$(echo \"$@\" | base64) | " +
                             ncSend(remoteHostPort) +
                             "\n" +
                             "while [[ ! -f \"" +
                             responseFile + "\" ]]; do\n" +
                             "sleep .25\n" +
                             "done\n" + "cat " +
                             responseFile +
                             "\n";
                     holder[0] = before + placeHolder;
                     while (holder[0].length() < endPos) {
                        holder[0] += " ";
                     }
                     if (placeHolder.length() > origLength) {
                        offset[0] = placeHolder.length() - origLength;
                     }
                     String after = beforeChange.substring(endPos);
                     holder[0] += after;
                  }
               }
               // No longer in a comment, deactivate this
               hostAnnotationActive[0] = false;
            }
         }
      });
      StringBuilder finalScript = new StringBuilder();
      for (Map.Entry<String, String> func : extracted.entrySet()) {
         StringBuilder funcWrapper = new StringBuilder("function ").append(
                 func.getKey()).append("() {\n");
         funcWrapper.append(func.getValue()).append("\n");
         funcWrapper.append("}\n");
         finalScript.append("echo 'define; ").append(func.getKey()).append(
                 " ").append(Utils.encodeBase64(
                 funcWrapper.toString())).append("' | ").append(Utils.ncSend(
                 remoteHostPort)).append("\n");
      }
      finalScript.append(holder[0].replaceAll("^#!.*", ""));
      return new RemoteFunctionReplacement(finalScript.toString(), extracted);
   }
   static <T> List<T> takeUntil(Queue<T> queue, Predicate<T> until) {
      List<T> items = new ArrayList<>();
      while (!queue.isEmpty()) {
         T peek = queue.peek();
         if (until.negate().test(peek)) {
            items.add(queue.poll());
         } else {
            break;
         }
      }
      return items;
   }
   static class RemoteFunctionReplacement {
      private final Map<String, String> extracted;
      private final String replacedScript;
      RemoteFunctionReplacement(String replacedScript,
                                Map<String, String> extracted) {
         this.replacedScript = replacedScript;
         this.extracted = extracted;
      }
      public Map<String, String> getExtracted() {
         return extracted;
      }
      public String getReplacedScript() {
         return replacedScript;
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
            T item = items.poll();
            if (item == null && !isDone.get()) {
               while (items.peek() == null) {
                  sleepQuietly(100);
               }
               item = items.poll();
            }
            return item;
         }
      })
              .takeWhile(Objects::nonNull);
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
}
