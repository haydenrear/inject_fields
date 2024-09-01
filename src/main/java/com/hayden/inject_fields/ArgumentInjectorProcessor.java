package com.hayden.inject_fields;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;

@SupportedAnnotationTypes("com.hayden.inject_fields.AutowireBean")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@Slf4j
public class ArgumentInjectorProcessor extends AbstractProcessor {
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
        File file = new File("out.log");
        file.createNewFile();
        var fos = new FileOutputStream("out.log");
        fos.write("hello\n".getBytes());
        for (Element element : roundEnv.getElementsAnnotatedWith(AutowireBean.class)) {
            fos.write(element.getSimpleName().toString().getBytes());
            generateBeanClass(element, fos);
        }
        fos.close();
        return true;
    }

    @SneakyThrows
    private void generateBeanClass(Element parameter, FileOutputStream fos) {
        // Get the fields from the @AutowireBean annotation
        var fields = getClasses(parameter, fos);
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

                            public class %s {
                                %s
                                %s
                                %s
                            }
                            """.formatted(pe.getQualifiedName().toString(), StringUtils.capitalize(parameter.getSimpleName().toString()), fieldsCreated, get, set);

                    try {

                        fos.write("writing".getBytes());
                        fos.write(parameter.asType().getKind().name().getBytes());
                        var sf = filer.createSourceFile(StringUtils.capitalize(parameter.asType().toString()));
                        try (PrintWriter writer = new PrintWriter(sf.openWriter())) {
                            writer.print(file);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });


    }

    record ClassValue(String simpleName, String qualifiedName, String fieldName) {}

    private ClassValue[] getClasses(Element parameter, FileOutputStream fos) throws IOException {
        return parameter.getAnnotationMirrors().stream()
                .filter(annot -> annot.getAnnotationType().toString().equals(AutowireBean.class.getName()))
                .findAny()
                .flatMap(e -> {
                    try {
                        var f = retrieveField(e);
                        String string = f.toString();
                        if (string.contains("{") && string.contains("}")) {
                            var s = string.split("\\{")[1].split("\\}")[0];
                            return Optional.of(Arrays.stream(s.split(",")).map(String::strip)
                                            .map(nextStr -> {
                                                String[] split = nextStr.split("\\.");
                                                split = Arrays.stream(split).filter(toR -> !toR.equals("class")).toArray(String[]::new);
                                                String simpleName = split[split.length - 1];
                                                return new ClassValue(simpleName, nextStr.replaceAll("\\.class", ""), StringUtils.uncapitalize(simpleName));
                                            })
                                    .toArray(ClassValue[]::new));
                        }
                        return Optional.empty();
                    } catch (Exception ignored) {
                        return Optional.empty();
                    }
                })
                .orElse(new ClassValue[] {});
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
