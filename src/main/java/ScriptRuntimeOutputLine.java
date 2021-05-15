import java.util.Objects;
import java.util.StringJoiner;

class ScriptRuntimeOutputLine {
   private final ScriptRuntimeOutputStreamType stream;
   private final String line;
   private final long time;
   public ScriptRuntimeOutputLine(ScriptRuntimeOutputStreamType stream,
                                  String line, long time) {
      this.stream = stream;
      this.line = line;
      this.time = time;
   }
   public ScriptRuntimeOutputStreamType getStream() {
      return stream;
   }
   public String getLine() {
      return line;
   }
   public long getTime() {
      return time;
   }
   @Override
   public String toString() {
      return new StringJoiner(", ",
              ScriptRuntimeOutputLine.class.getSimpleName() + "[", "]")
              .add("stream=" + getStream())
              .add("line='" + getLine() + "'")
              .add("time=" + getTime())
              .toString();
   }
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ScriptRuntimeOutputLine)) return false;
      ScriptRuntimeOutputLine that = (ScriptRuntimeOutputLine) o;
      return time == that.time &&
              stream == that.stream &&
              Objects.equals(line, that.line);
   }
   @Override
   public int hashCode() {
      return Objects.hash(stream, line, time);
   }
}
