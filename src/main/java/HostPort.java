import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

class HostPort {
   private final String host;
   private final int port;
   private HostPort(String host, int port) {
      this.host = host;
      this.port = port;
   }
   public static HostPort fromString(String hostPort) {
      if (StringUtils.isEmpty(hostPort)) {
         return null;
      }
      String host = "localhost";
      int port = 0;
      for (String part : hostPort.split(":")) {
         if (part.matches("^[0-9]+")) {
            port = Integer.parseInt(part);
         } else {
            host = part;
         }
      }
      return new HostPort(host, port);
   }
   public String getHost() {
      return host;
   }
   public int getPort() {
      return port;
   }
   @Override
   public String toString() {
      return String.format("%s:%s", host, port);
   }
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof HostPort)) return false;
      HostPort hostPort = (HostPort) o;
      return port == hostPort.port && Objects.equals(host,
              hostPort.host);
   }
   @Override
   public int hashCode() {
      return Objects.hash(host, port);
   }
}
