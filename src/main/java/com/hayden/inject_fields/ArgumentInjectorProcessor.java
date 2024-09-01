package com.hayden.inject_fields;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;

@SupportedAnnotationTypes("com.hayden.inject_fields.AutowireBean")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@Slf4j
public class ArgumentInjectorProcessor extends AbstractProcessor {

    private static String LOG_FILE;

    static {
        LOG_FILE = Optional.ofNullable(System.getenv("PROCESSOR_LOG")).orElse("out.log");
    }

    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
    }

    @SneakyThrows
    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        File file = new File(LOG_FILE);
        try (var fileWrite = new FileWriter(file);) {
            for (Element element : roundEnv.getElementsAnnotatedWith(AutowireBean.class)) {
                writeToFile(fileWrite, "Found autowire bean: %s.", element.getSimpleName());
                generateBeanClass(element, fileWrite);
            }
            return true;
        }
    }

    @SneakyThrows
    private void generateBeanClass(Element parameter, FileWriter fos) {
        // Get the fields from the @AutowireBean annotation
        var fields = getClasses(parameter, fos);
        var scope = getScope(parameter, fos);
        retrievePackageElement(parameter.getEnclosingElement())
                .ifPresent(pe -> {

                    var fieldsCreated = Arrays.stream(fields).map(c -> retrieveSingleField(c.qualifiedName, c.fieldName))
                            .collect(Collectors.joining("\n"));
                    var get = Arrays.stream(fields).map(c -> getField(c.qualifiedName, c.fieldName))
                            .collect(Collectors.joining("\n"));
                    var set = Arrays.stream(fields).map(c -> setField(c.qualifiedName, c.fieldName))
                            .collect(Collectors.joining("\n"));

                    String file = """
                            package %s;
                            
                            
                            @com.hayden.inject_fields.AutowireParameter
                            @org.springframework.stereotype.Component
                            @org.springframework.context.annotation.Scope("%s")
                            public class %s {
                            
                                %s
                            
                                %s
                            
                                %s
                            }
                            """.formatted(pe.getQualifiedName().toString(), scope, StringUtils.capitalize(parameter.getSimpleName().toString()), fieldsCreated, get, set);

                    writeToFile(fos, "\nThe following will be written as the generated parameter object: %s\n", file);

                    try {

                        writeToFile(fos, parameter.asType().getKind().name());
                        var sf = filer.createSourceFile(StringUtils.capitalize(parameter.asType().toString()));
                        try (PrintWriter writer = new PrintWriter(sf.openWriter())) {
                            writer.print(file);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });


    }

    record ClassValue(String simpleName,
                      String qualifiedName,
                      String fieldName) {
    }

    @SneakyThrows
    private String getScope(Element parameter, FileWriter fos) {
        writeToFile(fos, "Writing scope.");
        return parameter.getAnnotationMirrors().stream()
                .filter(annot -> annot.getAnnotationType().toString().equals(AutowireBean.class.getName()))
                .findAny()
                .map(e -> {
                    var f = retrieveScope(e);
                    writeToFile(fos, "Found scope: %s.", f);
                    var scopeFound = f.toString();
                    if (scopeFound.toLowerCase().contains("prototype")) {
                        return "prototype";
                    } else if (scopeFound.toLowerCase().contains("singleton")) {
                        return "singleton";
                    } else if (scopeFound.toLowerCase().contains("session")) {
                        return "session";
                    } else if (scopeFound.toLowerCase().contains("thread")) {
                        return "thread";
                    }
                    return "prototype";
                })
                .orElse("prototype");
    }

    /**
     * FYI the writes to the file only show up if the annotation processor fails.
     * @param fos
     * @param format
     */
    @SneakyThrows
    private static void writeToFile(FileWriter fos, Object ... format) {
        if (format.length == 0 || format[0] == null) {
            return;
        }
        if (format[0] instanceof String s) {
            fos.write(s.formatted(Arrays.copyOfRange(format, 1, format.length)));
        } else {
            fos.write(format[0].toString().formatted(Arrays.copyOfRange(format, 1, format.length)));
        }
    }

    private ClassValue[] getClasses(Element parameter, FileWriter fos) {
        return parameter.getAnnotationMirrors().stream()
                .filter(annot -> annot.getAnnotationType().toString().equals(AutowireBean.class.getName()))
                .findAny()
                .flatMap(e -> {
                    try {
                        writeToFile(fos, "");
                        return retrieveClassValueArray(e);
                    } catch (Exception exc) {
                        writeToFile(fos, exc.toString());
                        throw exc;
                    }
                })
                .orElse(new ClassValue[]{});
    }

    private static Optional<ClassValue[]> retrieveClassValueArray(AnnotationMirror e) {
        var f = retrieveField(e);
        String string = f.toString();
        if (string.contains("{") && string.contains("}")) {
            var s = string.split("\\{")[1].split("\\}")[0];
            return Optional.of(Arrays.stream(s.split(",")).map(String::strip)
                    .map(ArgumentInjectorProcessor::parseClassValue)
                    .toArray(ClassValue[]::new));
        }
        return Optional.empty();
    }

    private static ClassValue parseClassValue(String nextStr) {
        String[] split = nextStr.split("\\.");
        split = Arrays.stream(split).filter(toR -> !toR.equals("class")).toArray(String[]::new);
        String simpleName = split[split.length - 1];
        return new ClassValue(simpleName, nextStr.replaceAll("\\.class", ""), StringUtils.uncapitalize(simpleName));
    }

    private static AnnotationValue retrieveScope(AnnotationMirror e) {
        return e.getElementValues().entrySet().stream().filter(a -> a.getKey().getSimpleName().toString().contains("scope"))
                .map(Map.Entry::getValue)
                .findAny()
                .orElse(null);
    }

    private static AnnotationValue retrieveField(AnnotationMirror e) {
        return e.getElementValues().entrySet().stream().filter(a -> a.getKey().getSimpleName().toString().contains("field"))
                .map(Map.Entry::getValue)
                .findAny()
                .orElse(null);
    }

    private static Optional<PackageElement> retrievePackageElement(Element p) {
        while (p != null) {
            if (p instanceof PackageElement packageElement) {
                return Optional.of(packageElement);
            }

            p = p.getEnclosingElement();
        }

        return Optional.empty();
    }

    private static String getField(String fieldTy, String fieldName) {
        return """
                public %s get%s() {
                        return this.%s;
                    }
                """.formatted(fieldTy, StringUtils.capitalize(fieldName), fieldName);
    }

    private static String setField(String fieldTy, String fieldName) {
        return """
                @org.springframework.beans.factory.annotation.Autowired
                    public void set%s(%s %s) {
                        this.%s = %s;
                    }
                """.formatted(StringUtils.capitalize(fieldName), fieldTy, fieldName, fieldName, fieldName);
    }

    private static String retrieveSingleField(String fieldTy, String fieldName) {
        return """
                private %s %s;
                """.formatted(fieldTy, fieldName);
    }

}
