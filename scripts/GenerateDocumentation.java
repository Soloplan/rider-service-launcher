//-----------------------------------------------------------------------
// <copyright file="GenerateDocumentation.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Adds missing Javadoc comments to the plug-in's Java types, constructors, and methods.
 */
public final class GenerateDocumentation
{
  private GenerateDocumentation()
  {
  }

  /**
   * Adds documentation or verifies that all declarations are documented.
   *
   * @param arguments pass {@code --check} to report missing documentation without changing files
   * @throws Exception if sources cannot be parsed or updated
   */
  public static void main(String[] arguments) throws Exception
  {
    boolean checkOnly = List.of(arguments).contains("--check");
    Path root = Path.of("").toAbsolutePath();
    List<Path> files;
    try (Stream<Path> paths = Files.walk(root.resolve("src")))
    {
      files = paths.filter(path -> path.toString().endsWith(".java")).toList();
    }
    if (List.of(arguments).contains("--remove-generated"))
    {
      for (Path file : files)
      {
        String source = Files.readString(file);
        Files.writeString(file, removeGeneratedDocumentation(source));
      }
      return;
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    try (StandardJavaFileManager fileManager =
           compiler.getStandardFileManager(null, Locale.US, StandardCharsets.UTF_8))
    {
      Iterable<? extends JavaFileObject> sources =
        fileManager.getJavaFileObjectsFromPaths(files);
      JavacTask task = (JavacTask) compiler.getTask(
        null,
        fileManager,
        null,
        List.of("-proc:none"),
        null,
        sources
      );
      List<CompilationUnitTree> units = new ArrayList<>();
      task.parse().forEach(units::add);
      DocTrees docTrees = DocTrees.instance(task);
      SourcePositions positions = docTrees.getSourcePositions();
      List<Path> missingFiles = new ArrayList<>();

      for (CompilationUnitTree unit : units)
      {
        Path file = Path.of(unit.getSourceFile().toUri());
        String source = Files.readString(file);
        List<Insertion> insertions = findInsertions(unit, docTrees, positions, source);
        if (insertions.isEmpty())
        {
          continue;
        }
        missingFiles.add(root.relativize(file));
        if (!checkOnly)
        {
          Files.writeString(file, applyInsertions(source, insertions));
        }
      }

      if (!missingFiles.isEmpty())
      {
        missingFiles.forEach(System.err::println);
        if (checkOnly)
        {
          System.exit(1);
        }
      }
    }
  }

  private static List<Insertion> findInsertions(
    CompilationUnitTree unit,
    DocTrees docTrees,
    SourcePositions positions,
    String source
  )
  {
    List<Insertion> insertions = new ArrayList<>();
    new TreePathScanner<Void, Void>()
    {
      private final List<String> enclosingTypes = new ArrayList<>();

      @Override
      public Void visitClass(ClassTree declaration, Void unused)
      {
        addTypeDocumentation(declaration);
        enclosingTypes.add(declaration.getSimpleName().toString());
        try
        {
          return super.visitClass(declaration, unused);
        }
        finally
        {
          enclosingTypes.remove(enclosingTypes.size() - 1);
        }
      }

      @Override
      public Void visitMethod(MethodTree declaration, Void unused)
      {
        if (docTrees.getDocCommentTree(getCurrentPath()) == null)
        {
          int position = (int) positions.getStartPosition(unit, declaration);
          String indent = indentAt(source, position);
          String enclosingType = enclosingTypes.get(enclosingTypes.size() - 1);
          insertions.add(new Insertion(
            position - indent.length(),
            methodDocumentation(declaration, enclosingType, indent)
          ));
        }
        return super.visitMethod(declaration, unused);
      }

      private void addTypeDocumentation(ClassTree declaration)
      {
        TreePath path = getCurrentPath();
        if (docTrees.getDocCommentTree(path) != null || declaration.getSimpleName().isEmpty())
        {
          return;
        }
        int position = (int) positions.getStartPosition(unit, declaration);
        String indent = indentAt(source, position);
        String noun = humanize(declaration.getSimpleName().toString());
        String verb = declaration.getKind() == Tree.Kind.ENUM ? "Defines" : "Represents";
        insertions.add(new Insertion(
          position - indent.length(),
          indent + "/**\r\n"
            + indent + " * " + verb + " " + article(noun) + " " + noun + ".\r\n"
            + indent + " */\r\n"
        ));
      }
    }.scan(unit, null);
    return insertions;
  }

  private static String methodDocumentation(MethodTree declaration, String enclosingType, String indent)
  {
    if (hasOverrideAnnotation(declaration))
    {
      return indent + "/** {@inheritDoc} */\r\n";
    }

    boolean constructor = declaration.getReturnType() == null;
    String name = constructor
      ? enclosingType
      : declaration.getName().toString();
    String summary = constructor
      ? "Creates a new {@code " + name + "} instance."
      : methodSummary(name, declaration.getReturnType().toString());
    StringBuilder documentation = new StringBuilder();
    documentation.append(indent).append("/**\r\n");
    documentation.append(indent).append(" * ").append(summary).append("\r\n");

    if (!declaration.getParameters().isEmpty()
      || (!constructor && !"void".equals(declaration.getReturnType().toString()))
      || !declaration.getThrows().isEmpty())
    {
      documentation.append(indent).append(" *\r\n");
    }
    declaration.getParameters().forEach(parameter ->
      documentation.append(indent)
        .append(" * @param ")
        .append(parameter.getName())
        .append(" ")
        .append(parameterDescription(parameter.getName().toString(), parameter.getType().toString()))
        .append("\r\n")
    );
    if (!constructor && !"void".equals(declaration.getReturnType().toString()))
    {
      documentation.append(indent)
        .append(" * @return ")
        .append(returnDescription(name, declaration.getReturnType().toString()))
        .append("\r\n");
    }
    declaration.getThrows().forEach(exception ->
      documentation.append(indent)
        .append(" * @throws ")
        .append(exception)
        .append(" if the operation cannot be completed\r\n")
    );
    documentation.append(indent).append(" */\r\n");
    return documentation.toString();
  }

  private static boolean hasOverrideAnnotation(MethodTree declaration)
  {
    return declaration.getModifiers().getAnnotations().stream()
      .anyMatch(annotation -> annotation.getAnnotationType().toString().endsWith("Override"));
  }

  private static String methodSummary(String name, String returnType)
  {
    String phrase = humanize(name);
    if (name.startsWith("get") && name.length() > 3)
    {
      return "Returns the " + humanize(name.substring(3)) + ".";
    }
    if (isQuestion(name, returnType))
    {
      return "Determines whether " + questionPhrase(name) + ".";
    }

    String[][] verbs =
    {
      {"create", "Creates"
      },
      {"load", "Loads"
      },
      {"read", "Reads"
      },
      {"write", "Writes"
      },
      {"export", "Exports"
      },
      {"import", "Imports"
      },
      {"add", "Adds"
      },
      {"remove", "Removes"
      },
      {"replace", "Replaces"
      },
      {"move", "Moves"
      },
      {"normalize", "Normalizes"
      },
      {"ensure", "Ensures"
      },
      {"refresh", "Refreshes"
      },
      {"rebuild", "Rebuilds"
      },
      {"start", "Starts"
      },
      {"stop", "Stops"
      },
      {"restart", "Restarts"
      },
      {"update", "Updates"
      },
      {"paint", "Paints"
      },
      {"show", "Shows"
      },
      {"edit", "Edits"
      },
      {"toggle", "Toggles"
      },
      {"configure", "Configures"
      },
      {"apply", "Applies"
      },
      {"run", "Runs"
      },
      {"copy", "Copies"
      },
      {"merge", "Merges"
      },
      {"calculate", "Calculates"
      },
      {"find", "Finds"
      },
      {"select", "Selects"
      },
      {"set", "Sets"
      }
    };
    for (String[] verb : verbs)
    {
      if (name.startsWith(verb[0]))
      {
        String object = humanize(name.substring(verb[0].length()));
        return verb[1] + (object.isEmpty() ? " the operation." : " the " + object + ".");
      }
    }
    return "void".equals(returnType)
      ? "Performs the " + phrase + " operation."
      : "Returns the result of " + phrase + ".";
  }

  private static boolean isQuestion(String name, String returnType)
  {
    return "boolean".equals(returnType)
      || name.startsWith("is")
      || name.startsWith("has")
      || name.startsWith("can")
      || name.startsWith("contains");
  }

  private static String questionPhrase(String name)
  {
    for (String prefix : List.of("is", "has", "can", "contains"))
    {
      if (name.startsWith(prefix) && name.length() > prefix.length())
      {
        return humanize(name.substring(prefix.length()));
      }
    }
    return humanize(name);
  }

  private static String parameterDescription(String name, String type)
  {
    if ("boolean".equals(type) || name.startsWith("is") || name.startsWith("has"))
    {
      return "whether " + questionPhrase(name);
    }
    return "the " + humanize(name);
  }

  private static String returnDescription(String name, String type)
  {
    if ("boolean".equals(type))
    {
      return "whether " + questionPhrase(name);
    }
    if (name.startsWith("get") && name.length() > 3)
    {
      return "the " + humanize(name.substring(3));
    }
    return "the " + humanize(name) + " result";
  }

  private static String humanize(String value)
  {
    if (value == null || value.isEmpty())
    {
      return "";
    }
    return value
      .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
      .replace('_', ' ')
      .toLowerCase(Locale.US);
  }

  private static String article(String noun)
  {
    return noun.matches("^[aeiou].*") ? "an" : "a";
  }

  private static String indentAt(String source, int position)
  {
    int lineStart = source.lastIndexOf('\n', Math.max(0, position - 1)) + 1;
    int index = lineStart;
    while (index < source.length() && source.charAt(index) == ' ')
    {
      index++;
    }
    return source.substring(lineStart, index);
  }

  private static String applyInsertions(String source, List<Insertion> insertions)
  {
    StringBuilder result = new StringBuilder(source);
    insertions.stream()
      .sorted(Comparator.comparingInt(Insertion::position).reversed())
      .forEach(insertion -> result.insert(insertion.position(), insertion.text()));
    return result.toString();
  }

  private static String removeGeneratedDocumentation(String source)
  {
    return Pattern.compile("(?ms)^([ ]*)/\\*\\*.*?\\*/\\r?\\n")
      .matcher(source)
      .replaceAll(
      match ->
      {
        String doubledIndent = match.group(1);
        return doubledIndent.substring(0, doubledIndent.length() / 2);
      }
      );
  }

  private record Insertion(int position, String text)
  {
  }
}
