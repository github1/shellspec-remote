import org.junit.Test;

import java.nio.file.Paths;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class MainTest {
   @Test
   public void itRunsWithStreamOutput() {
      assertThat(doRun(() -> new Main("bash").execute(
              Paths.get(System.getProperty("user.dir")).resolve(
                      "src/test/resources/fixture-main.sh").toString(), "-r",
              "localhost:9998", "-e", "FUNC=func_stream_output"))).isEqualTo(0);
   }
   @Test
   public void itCanFail() {
      assertThat(doRun(() -> new Main("bash").execute(
              Paths.get(System.getProperty("user.dir")).resolve(
                      "src/test/resources/fixture-main.sh").toString(), "-r",
              "localhost:9998", "-e", "FUNC=func_fails"))).isEqualTo(1);
   }
   @Test
   public void itCanRunBackgroundProcesses() {
      assertThat(doRun(() -> new Main("bash").execute(
              Paths.get(System.getProperty("user.dir")).resolve(
                      "src/test/resources/fixture-main.sh").toString(), "-r",
              "localhost:9998", "-e", "FUNC=background_process"))).isEqualTo(0);
   }
   @Test
   public void itAllowsLocalFunctionsToCallRemoteFunctions() {
      assertThat(doRun(() -> new Main("bash").execute(
              Paths.get(System.getProperty("user.dir")).resolve(
                      "src/test/resources/fixture-main.sh").toString(), "-r",
              "localhost:9998", "-e", "FUNC=local_pipe_from_remote"))).isEqualTo(0);
   }
   private int doRun(Supplier<Integer> runnable) {
      int status;
      Lock lock = new ReentrantLock();
      Thread t = new Thread(() -> {
         try {
            ScriptRuntime runtime = new ScriptRuntime();
            Process process = runtime.execute(
                    new String[]{"bash", "-c", "nc -kl 9998 | " + Paths.get(
                            System.getProperty("user.dir")).resolve(
                            "bridge.sh").toAbsolutePath()},
                    outputLine -> System.err.println(
                            outputLine.getLine()));
            while (!lock.tryLock()) {
               try {
                  Thread.sleep(1000);
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }
            process.destroyForcibly();
            runtime.execute(new String[]{"pkill", "-f", "nc -kl"},
                    outputLine -> System.err.println(
                            outputLine.getLine())).waitFor();
         } catch (Exception e) {
            e.printStackTrace();
         }
      });
      lock.lock();
      t.start();
      status = runnable.get();
      lock.unlock();
      try {
         t.join();
      } catch (Exception e) {
         e.printStackTrace();
      }
      return status;
   }
}
