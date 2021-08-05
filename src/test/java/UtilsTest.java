import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
   @Test
   public void itFindsFiles() {
      assertThat(Utils.findFiles(
              Paths.get(System.getProperty("user.dir")).resolve("docker"),
              p -> true).map(p -> p.getFileName().toString()).collect(
              Collectors.toList())).containsExactly("Dockerfile",
              "entrypoint.sh");
      assertThat(Utils.findFiles(
              Paths.get(System.getProperty("user.dir")).resolve("docker"),
              p -> p.getFileName().toString().endsWith(".sh")).map(
              p -> p.getFileName().toString()).collect(
              Collectors.toList())).containsExactly(
              "entrypoint.sh");
   }
   @Test
   public void itReplacesRemoteFunctionsWithRemoteExecutionScripts() {
      Utils.RemoteFunctionReplacement replaced = Utils.replaceRemoteFunctions(
              new Utils.Spec(Paths.get("fake.txt"),
                      "# @OnHost\nfunction something() {\nlocal ARG1=${1}\necho ${1} | \\ \n egrep '^function foo(a)$'\n}\nfoo=bar\nfunction normal_func() {\necho hi\n}\n# bloo\n# @OnHost\n# blah\nfunction something_else() {\n echo hi\n}"),
              HostPort.fromString("localhost:1234"));
      assertThat(replaced.getExtracted()).hasSize(2);
      assertThat(replaced.getExtracted().values().iterator().next()).contains(
              "ARG1=${1}");
      assertThat(replaced.getReplacedScript()).contains("echo 'invoke;");
   }
   @Test
   public void itReplacesMultipleRemoteFunctionsWithRemoteExecutionScripts() throws Exception {
      String fixture = IOUtils.toString(Objects.requireNonNull(
              UtilsTest.class.getClassLoader().getResourceAsStream(
                      "fixture-1.sh")), StandardCharsets.UTF_8);
      String fixtureReplaced = IOUtils.toString(Objects.requireNonNull(
              UtilsTest.class.getClassLoader().getResourceAsStream(
                      "fixture-1.replaced.sh")), StandardCharsets.UTF_8);
      Utils.RemoteFunctionReplacement replaced = Utils.replaceRemoteFunctions(
              new Utils.Spec(Paths.get("fake.txt"), fixture),
              HostPort.fromString("localhost:1234"),
              fn -> fn);
      assertThat(replaced.getReplacedScript()).isEqualTo(fixtureReplaced);
      List<String> replacements = new ArrayList<>(
              replaced.getExtracted().values());
      assertThat(replacements).containsExactly("hostname", "hostname",
              "echo \"market_segment\"");
   }
   @Test
   public void itRewritesFailureLines() throws Exception {
      String fixture = IOUtils.toString(Objects.requireNonNull(
              UtilsTest.class.getClassLoader().getResourceAsStream(
                      "fixture-1.sh")), StandardCharsets.UTF_8);
      String fixtureReplaced = IOUtils.toString(Objects.requireNonNull(
              UtilsTest.class.getClassLoader().getResourceAsStream(
                      "fixture-1.replaced.sh")), StandardCharsets.UTF_8);
      List<Utils.TempSpec> tempSpecs = new ArrayList<>();
      tempSpecs.add(new Utils.TempSpec(
              Paths.get("./tmpe50ae65c-eb3c-4aa3-850c-567e179ca981_spec.sh"),
              new Utils.RemoteFunctionReplacement(
                      new Utils.Spec(Paths.get("fake.txt"), fixture),
                      fixtureReplaced,
                      Collections.emptyMap())));
      ScriptRuntime runtime = new ScriptRuntime();
      int status = runtime.execute(
              new String[]{"cat", Paths.get(
                      System.getProperty("user.dir")).resolve(
                      "src/test/resources/fixture-output-1.txt").toAbsolutePath().toString()},
              outputLine -> {
                 String line = Utils.rewriteFailureLines(tempSpecs,
                         outputLine.getLine());
                 System.err.println(line);
              }).waitFor();
      assertThat(status).isEqualTo(0);
   }
}
