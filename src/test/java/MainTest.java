import org.junit.Test;

import java.nio.file.Paths;

public class MainTest {
   @Test
   public void itRuns() {
      Main m = new Main();
      m.execute(Paths.get(System.getProperty("user.dir")).resolve(
              "api_scratch.sh").toString(), "-r", "localhost:9998");
   }
}
