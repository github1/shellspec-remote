import org.junit.Test;

import java.nio.file.Paths;
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
              "# @OnHost\nfunction something() {\nlocal ARG1=${1}\necho ${1} | \\ \n egrep '^function foo(a)$'\n}\nfoo=bar\nfunction normal_func() {\necho hi\n}\n# bloo\n# @OnHost\n# blah\nfunction something_else() {\n echo hi\n}",
              HostPort.fromString("localhost:1234"));
      assertThat(replaced.getExtracted()).hasSize(2);
      assertThat(replaced.getExtracted().values().iterator().next()).contains(
              "ARG1=${1}");
      assertThat(replaced.getReplacedScript()).contains("echo 'invoke;");
   }
}
