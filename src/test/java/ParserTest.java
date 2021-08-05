import org.junit.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ParserTest {
   @Test
   public void testTokenize() {
      assertThat(Parser
              .tokenize("foo() { echo \"bar\" }").map(
                      Parser.Token::getValue)).containsExactly("foo()", "{",
              "echo", "\"bar\"", "}");
   }
   @Test
   public void testParse() {
      String input = "foo() { echo \"bar\"; echo \"baz\"; }";
      Parser.Node node = Parser.parse(input);
      assertThat(node.getType()).isEqualTo("script");
      Parser.Node func = node.getChildren().get(0);
      assertThat(func.getType()).isEqualTo("function");
      assertThat(func.getChildren().get(0).getChildren().stream().map(
              Parser.Token::getValue).collect(
              Collectors.toList())).containsExactly("echo", "\"bar\"", "echo",
              "\"baz\"");
      assertThat(func.getStartPos()).isEqualTo(0);
      assertThat(func.getEndPos()).isEqualTo(33);
      assertThat(
              input.substring(func.getStartPos(), func.getEndPos())).isEqualTo(
              input);
   }
   @Test
   public void testParseNewLines() {
      String input = "foo() {\necho \"bar\"\necho \"baz\"\n}";
      Parser.Node node = Parser.parse(input);
      assertThat(node.getType()).isEqualTo("script");
      Parser.Node func = node.getChildren().get(0);
      assertThat(func.getType()).isEqualTo("function");
      assertThat(func.getChildren().get(0).getChildren().stream().map(
              Parser.Token::getValue).collect(
              Collectors.toList())).containsExactly("echo", "\"bar\"", "echo",
              "\"baz\"");
      assertThat(func.getStartPos()).isEqualTo(0);
      assertThat(func.getEndPos()).isEqualTo(31);
      assertThat(
              input.substring(func.getStartPos(), func.getEndPos())).isEqualTo(
              input);
   }
   @Test
   public void testParseFunctionWithVariable() {
      String input = "foo() {echo ${VAR}; }";
      Parser.Node node = Parser.parse(input);
      assertThat(node.getType()).isEqualTo("script");
      Parser.Node func = node.getChildren().get(0);
      assertThat(func.getType()).isEqualTo("function");
      assertThat(func.getChildren().get(0).getChildren().stream().map(
              Parser.Token::getValue).collect(
              Collectors.toList())).containsExactly("echo", "${VAR}");
      assertThat(func.getStartPos()).isEqualTo(0);
      assertThat(func.getEndPos()).isEqualTo(21);
      assertThat(
              input.substring(func.getStartPos(), func.getEndPos())).isEqualTo(
              input);
   }
   @Test
   public void testParseVariables() {
      assertThat(
              Parser.parse("$VAR").getChildren().get(0).getValue()).isEqualTo(
              "$VAR");
      assertThat(
              Parser.parse("$VAR $BAR").getChildren().get(1).getValue()).isEqualTo(
              "$BAR");
      assertThat(
              Parser.parse("$VAR $BAR ${BAZ}").getChildren().get(2).getValue()).isEqualTo(
              "${BAZ}");
   }
}
