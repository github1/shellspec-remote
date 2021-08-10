import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

class ScriptRuntime {
   private static final Logger LOG = LoggerFactory.getLogger(
           ScriptRuntime.class);
   private final ExecutorService executorService;
   private final Map<String, String> environment;
   public ScriptRuntime() {
      this.executorService = Executors.newFixedThreadPool(3);
      this.environment = new HashMap<>();
   }
   public ScriptRuntime withEnvVar(String name, String value) {
      environment.put(name, value);
      return this;
   }
   public Process execute(String[] command,
                          Consumer<ScriptRuntimeOutputLine> lineConsumer) {
      try {
         if (environment != null && !environment.isEmpty()) {
            String envVars = environment.keySet().stream().map(
                    k -> String.format("export %s='%s'", k,
                            environment.get(k))).collect(
                    Collectors.joining(" && "));
            String mainCommand = String.join(" ", command);
            command = new String[]{"bash", "-c", envVars + " && exec " + mainCommand};
         }
         LOG.debug("execute {}", Arrays.toString(command));
         Process process = Runtime.getRuntime().exec(command);
         Function<Consumer<ScriptRuntimeOutputLine>, Function<ScriptRuntimeOutputStreamType, Consumer<String>>> outputLine = consumer -> dataStream -> line -> {
            ScriptRuntimeOutputLine l = new ScriptRuntimeOutputLine(dataStream,
                    line,
                    System.currentTimeMillis());
            LOG.debug("{}", l);
            consumer.accept(l);
         };
         CompletableFuture.allOf(CompletableFuture.runAsync(
                         new StreamGobbler(process.getInputStream(),
                                 outputLine.apply(lineConsumer).apply(
                                         ScriptRuntimeOutputStreamType.STDOUT)),
                         executorService),
                 CompletableFuture.runAsync(
                         new StreamGobbler(process.getErrorStream(),
                                 outputLine.apply(lineConsumer).apply(
                                         ScriptRuntimeOutputStreamType.STDERR)),
                         executorService));
         return process;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }
   private static class StreamGobbler implements Runnable {
      private final InputStream inputStream;
      private final Consumer<String> consumer;
      public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
         this.inputStream = inputStream;
         this.consumer = consumer;
      }
      @Override
      public void run() {
         new BufferedReader(new InputStreamReader(inputStream)).lines()
                 .forEach(consumer);
      }
   }
   private static Map<String, String> mergeEnv(Map<String, String> env) {
      Map<String, String> merged = new HashMap<>(System.getenv());
      if (env != null) {
         merged.putAll(env);
      }
      return merged;
   }
}
