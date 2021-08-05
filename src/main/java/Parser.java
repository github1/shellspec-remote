import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Parser {
   private final Queue<Token> tokens;
   public Parser(Queue<Token> tokens) {
      this.tokens = tokens;
   }
   static Stream<Token> tokenize(final String script) {
      return Utils.generate(consumer -> {
         int c = 0;
         String[] chars = script.split("");
         String inQuote = null;
         String prevChar = null;
         StringBuilder buffer = new StringBuilder();
         int lineNumber = 0;
         while (c < chars.length) {
            String currChar = chars[c++];
            if (currChar.equals("\n")) {
               lineNumber++;
            }
            boolean isEscaped = "\\".equals(prevChar);
            if (currChar.equals("'") || currChar.equals("\"")) {
               if (inQuote == null) {
                  inQuote = currChar;
               } else if (currChar.equals(inQuote) && !isEscaped) {
                  inQuote = null;
               }
            }
            boolean isTerminatingChar = ("=".equals(currChar)
                    || " ".equals(currChar)
                    || "\n".equals(currChar)
                    || "{".equals(currChar) && !"&".equals(prevChar)
                    || "}".equals(currChar)
                    || ";".equals(currChar)) && inQuote == null;
            if (isTerminatingChar || c == chars.length) {
               if (c == chars.length && !isTerminatingChar) {
                  buffer.append(currChar);
               }
               String token = buffer.toString().trim();
               buffer = new StringBuilder();
               if (!token.trim().isEmpty()) {
                  consumer.accept(
                          new Token(token, lineNumber,
                                  c - token.length() - 1));
               }
               if ("\n".equals(currChar)
                       || "=".equals(currChar)
                       || "{".equals(currChar)
                       || "}".equals(currChar)) {
                  consumer.accept(new Token(currChar, lineNumber, c));
               }
            } else {
               buffer.append(currChar);
            }
            prevChar = currChar;
         }
      });
   }
   static Node parse(String script) {
      Parser parser = new Parser(Parser.tokenize(script).collect(
              Collectors.toCollection(ArrayDeque::new)));
      Node root = new Node("script", "", 0, 0);
      parser.parseScript(root, t -> parser.tokens.isEmpty());
      return root.children.get(0);
   }
   private void parseScript(Node parent, Predicate<Token> parseUntil) {
      Node script = new Node("script", "", parent.getLineNumber(),
              parent.getStartPos() + parent.getValue().length());
      Token peek = peek();
      while (!parseUntil.test(peek)) {
         if (peek == null) {
            break;
         }
         if (Token.of("#").equals(peek)) {
            parseComment(script);
         } else if (Token.of("$").equals(peek)) {
            parseVariable(script);
         } else if (Token.of("function").equals(
                 peek) || peek.getValue().contains("()")) {
            parseFunction(script);
         } else if (peek.getValue().equals("local")) {
            parseDeclaration(script);
         } else {
            Token polled = tokens.poll();
            if (!Token.of("{").equals(polled) && !Token.of("}").equals(
                    polled) && !Token.of("\n").equals(polled) && !Token.of(
                    " ").equals(polled) && !Token.of(";").equals(polled)) {
               script.addChild(new Node("unknown",
                       Collections.singletonList(polled)));
            } else {
               script.extendEndPosTo(polled);
            }
         }
         peek = peek();
      }
      parent.addChild(script);
   }
   private void parseComment(Node parent) {
      List<Token> comment = takeUntil(tokens,
              Token.of("\n")::equals);
      // New line
      tokens.poll();
      parent.addChild(new Node("comment",
              comment.stream().map(t -> t.value).collect(
                      Collectors.joining(" ")), comment.get(0).lineNumber,
              comment.get(0).startPos));
   }
   private void parseVariable(Node parent) {
      Node variable = new Node("variable", takeMatching(tokens,
              Pattern.compile("(?i)(\\$\\{?[^};]+}?)")));
      parent.addChild(variable);
   }
   private void parseFunction(Node parent) {
      if (Token.of("function").equals(tokens.peek())) {
         tokens.poll();
      }
      Node function = new Node("function", takeUntil(tokens,
              Token.of("{")::equals));
      parseScript(function, Token.of("}")::equals);
      if (Token.of("}").equals(tokens.peek())) {
         function.extendEndPosTo(tokens.poll());
      }
      parent.addChild(function);
   }
   private void parseDeclaration(Node parent) {
      Token val = tokens.peek();
      if (val == null) {
         return;
      }
      if ("local".equals(val.getValue())) {
         tokens.poll();
      }
      List<Token> name = takeUntil(tokens, Token.of("=")::equals);
      // Operator
      tokens.poll();
      Node declaration = new Node("declaration", name);
      parseValue(declaration);
      parent.addChild(declaration);
   }
   private void parseValue(Node parent) {
      parent.addChild(new Node("value",
              takeUntil(tokens, Token.of("\n")::equals)));
   }
   private Token peek() {
      return tokens.peek();
   }
   private static <T> List<T> takeUntil(Queue<T> queue, Predicate<T> until) {
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
   private static List<Token> takeMatching(Queue<Token> queue,
                                           Pattern pattern) {
      StringBuilder val = new StringBuilder();
      int space = -1;
      List<Token> polled = new ArrayList<>();
      while (space < 3) {
         Token t = queue.poll();
         if (t != null) {
            if (!polled.isEmpty()) {
               space = t.getStartPos() - polled.get(
                       polled.size() - 1).getStartPos();
            }
            ;
            polled.add(t);
            val.append(t.getValue());
         } else {
            break;
         }
      }
      Matcher matcher = pattern.matcher(val.toString() + ";");
      if (matcher.find()) {
         return polled;
      } else {
         queue.addAll(polled);
      }
      return Collections.emptyList();
   }
   static class Token {
      private final String value;
      private final int lineNumber;
      private final int startPos;
      Token(String value, int lineNumber, int startPos) {
         this.value = value;
         this.lineNumber = lineNumber;
         this.startPos = startPos;
      }
      public static Token of(String value) {
         return new Token(value, 0, 0);
      }
      public String getValue() {
         return value;
      }
      public int getLineNumber() {
         return lineNumber;
      }
      public int getStartPos() {
         return startPos;
      }
      @Override
      public String toString() {
         return new StringJoiner(", ", Token.class.getSimpleName() + "[",
                 "]")
                 .add("value='" + value + "'")
                 .add("lineNumber=" + lineNumber)
                 .add("startPos=" + startPos)
                 .toString();
      }
      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (!(o instanceof Token)) return false;
         Token token = (Token) o;
         return Objects.equals(value, token.value);
      }
      @Override
      public int hashCode() {
         return Objects.hash(value);
      }
   }

   static class Node extends Token {
      private final String type;
      private final List<Node> children = new ArrayList<>();
      private int endPos;
      Node(String type, List<Token> tokens) {
         this(type, tokens.stream().map(t -> t.value).collect(
                         Collectors.joining("")),
                 tokens.isEmpty() ? 0 : tokens.get(0).lineNumber,
                 tokens.isEmpty() ? 0 : tokens.get(0).startPos);
      }
      Node(String type, String value, int lineNumber, int startPos) {
         super(value, lineNumber, startPos);
         this.type = type;
         this.endPos = startPos + value.length();
      }
      public String getType() {
         return type;
      }
      public List<Node> getChildren() {
         return children;
      }
      public Node getLastChild() {
         return getChildren().get(
                 getChildren().size() - 1);
      }
      public int getEndPos() {
         return endPos;
      }
      void addChild(Node node) {
         endPos = Math.max(endPos, node.getEndPos());
         children.add(node);
      }
      void extendEndPosTo(Token token) {
         endPos = Math.max(endPos, token.getStartPos());
      }
      void accept(NodeVisitor nodeVisitor) {
         nodeVisitor.visit(this);
         children.forEach(child -> child.accept(nodeVisitor));
      }
      @Override
      public String toString() {
         return new StringJoiner(", ", getType().toUpperCase() + "[",
                 "]")
                 .add("value='" + getValue() + "'")
                 .add("lineNumber=" + getLineNumber())
                 .add("startPos=" + getStartPos())
                 .add("children=" + children)
                 .toString();
      }
   }

   static class NodeVisitor {
      public void visit(Node node) {
      }
   }
}
